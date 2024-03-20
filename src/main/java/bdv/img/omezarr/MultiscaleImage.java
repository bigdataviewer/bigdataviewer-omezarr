/*-
 * #%L
 * This package provides multi image OME-Zarr support in bigdataviewer.
 * %%
 * Copyright (C) 2022 - 2023 BigDataViewer developers.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * #L%
 */
package bdv.img.omezarr;

import bdv.cache.SharedQueue;
import bdv.util.volatiles.VolatileTypeMatcher;
import bdv.util.volatiles.VolatileViews;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.DefaultAwsRegionProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryMode;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.Volatile;
import net.imglib2.cache.img.CachedCellImg;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.ByteType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.type.numeric.integer.LongType;
import net.imglib2.type.numeric.integer.ShortType;
import net.imglib2.type.numeric.integer.UnsignedByteType;
import net.imglib2.type.numeric.integer.UnsignedIntType;
import net.imglib2.type.numeric.integer.UnsignedLongType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.util.Cast;
import org.janelia.saalfeldlab.n5.DataType;
import org.janelia.saalfeldlab.n5.DatasetAttributes;
import org.janelia.saalfeldlab.n5.N5Exception;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.s3.AmazonS3KeyValueAccess;
import org.janelia.saalfeldlab.n5.zarr.N5ZarrReader;
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueReader;

import static bdv.img.omezarr.Multiscales.MULTI_SCALE_KEY;

/**
 * As n5-zarr already provides dimensions in the java order, this class also stores dimensions in the java order.
 *
 * @param <T> Image type.
 * @param <V> Volatile image type.
 */
public class MultiscaleImage< T extends NativeType< T > & RealType< T >, V extends Volatile< T > & NativeType< V > & RealType< V > >
{
	/**
	 * Helper class to store the base multiscale image path and other
	 * access credentials for creating readed instances either on local FS or from S3.
	 *
	 * Instances that are obtained by the copy-constructor after the credentials
	 * or the connection is set, will share the same AWS credential
	 * and connection instance.
	 *
	 */
	public static class ZarrKeyValueReaderBuilder {
		private AmazonS3 s3Client;
		private AWSCredentials s3Credentials = null;
		private final boolean s3Mode;
		private final String bucketName;
		private String multiscalePath;
		private String s3Region = null;

		/**
		 * Constructor for S3 reader.
		 *
		 * @param s3Client S3 access client. Instance is not copied.
		 * @param bucketName Bucket name for access.
		 * @param multiscalePath Base path within the bucket.
		 */
		public ZarrKeyValueReaderBuilder(final AmazonS3 s3Client, final String bucketName,
										 final String multiscalePath) {
			this.multiscalePath = multiscalePath;
			this.s3Client = s3Client;
			this.bucketName = bucketName;
			this.s3Mode = true;
		}

		public ZarrKeyValueReaderBuilder(final String bucketName,
										 final String multiscalePath)
		{
			this(null, bucketName, multiscalePath);
		}
		public void setCredentials(AWSCredentials s3Credentials)
		{
			if (!s3Mode)
				return;
			this.s3Credentials = s3Credentials;
		}

		public void setRegion(final String region)
		{
			if (!s3Mode)
				return;
			this.s3Region = region;
		}

		public void initS3Client()
		{
			if (!s3Mode)
				return;

			if (s3Region==null)
				s3Region = new DefaultAwsRegionProviderChain().getRegion();

			if (s3Client==null)
			{
				final AWSStaticCredentialsProvider credentialsProvider;
				if (s3Credentials==null)
				{
					try {
						s3Credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
						System.out.println( "Got credentials from default chain." );
					}
					catch(final Exception e) {
						System.out.println( "Could not load AWS credentials, falling back to anonymous." );
					}
				}
				credentialsProvider = new AWSStaticCredentialsProvider(s3Credentials == null ? new AnonymousAWSCredentials() : s3Credentials);
				final ClientConfiguration s3Conf = new ClientConfiguration().withRetryPolicy(PredefinedRetryPolicies.getDefaultRetryPolicyWithCustomMaxRetries(32));
				s3Client = AmazonS3ClientBuilder.standard().withRegion(s3Region).withCredentials(credentialsProvider).withClientConfiguration(s3Conf).build();
			}
		}

		/**
		 * Shallow copy constructor.
		 *
		 * Make shallow copy. S3 client instance will be shared.
		 *
		 * @param src Source instance.
		 */
		public ZarrKeyValueReaderBuilder(final ZarrKeyValueReaderBuilder src)
		{
			this.multiscalePath = src.multiscalePath;
			this.s3Credentials = src.s3Credentials;
			this.s3Client = src.s3Client;
			this.bucketName = src.bucketName;
			this.s3Mode = src.s3Mode;
		}

		/**
		 * Constructor for filesystem access.
		 * @param multiscalePath Base path for zarr images.
		 */
		public ZarrKeyValueReaderBuilder(final String multiscalePath) {
			this.multiscalePath = multiscalePath;
			this.s3Client = null;
			this.bucketName = null;
			this.s3Mode = false;
		}

		/**
		 * Build new reader instance.
		 *
		 * @return New N5ZarrReader instance for filesystem. New ZarrKeyValueReader instance for S3 access.
		 * @throws N5Exception New reader instance constructor exception propagates up.
		 */
		public ZarrKeyValueReader build() throws N5Exception {
			if (s3Mode) {
				initS3Client();
				final AmazonS3KeyValueAccess s3KeyValueAccess = new AmazonS3KeyValueAccess(s3Client, bucketName, false);
				return new ZarrKeyValueReader(s3KeyValueAccess, multiscalePath, new GsonBuilder(),
						false, false, true);
			} else {
				return new N5ZarrReader(multiscalePath);
			}
		}

		/**
		 * New reader builder for a sub-image.
		 *
		 * @param subPath Sub-path for sub-image in the zarr hierarchy.
		 * @return New shallow-copy builder instance pointing to base path/subpath.
		 */
		public ZarrKeyValueReaderBuilder getSubImage(final String subPath) {
			final ZarrKeyValueReaderBuilder z = new ZarrKeyValueReaderBuilder(this);
			if (s3Mode) {
				z.multiscalePath = z.multiscalePath + "/" + subPath;
			} else {
				z.multiscalePath = Paths.get(z.multiscalePath, subPath).toString();
			}
			return z;
		}

		/**
		 * @return Stored base path in this builder instance.
		 */
		public String getMultiscalePath()
		{
			return multiscalePath;
		}

		/**
		 * @return Stored bucket name or null.
		 */
		public String getBucketName(){ return bucketName; }
	}
	private final ZarrKeyValueReaderBuilder zarrKeyValueReaderBuilder;
	private SharedQueue queue;

	private int numResolutions;

	private long[] dimensions; // Java order of axes

	private T type;

	private V volatileType;

	private CachedCellImg< T, ? >[] imgs;

	private RandomAccessibleInterval< V >[] vimgs;

	private Multiscales multiscales;

	private int multiscaleArrayIndex = 0; // TODO (see comments within code)

	private DataType dataType;

	private long[][] multiDimensions; // Java order of axes

	private DataType[] multiDataType;

	/**
	 * TODO
	 */

	public MultiscaleImage(
			final ZarrKeyValueReaderBuilder keyValueReaderBuilder)
	{
		this.zarrKeyValueReaderBuilder=keyValueReaderBuilder;
	}
//	public MultiscaleImage(
//			final String multiscalePath)
//	{
//		this(new ZarrKeyValueReaderBuilder(multiscalePath));
//	}

	/**
	 * The delayed initialization of images is to have the shared queue set by ZarrImageLoader first
	 */
	private void initImages()
	{
		if (imgs != null)
			return;
//		try {
//			final ZarrKeyValueReader zarrKeyValueReader = zarrKeyValueReaderBuilder.create();
			// Initialize the images for all resolutions.
			//
			// TODO only on demand
			// See N5ImageLoader.prepareCachedImage
			imgs = new CachedCellImg[ numResolutions ];
			vimgs = new RandomAccessibleInterval[ numResolutions ];

			for ( int resolution = 0; resolution < numResolutions; ++resolution )
			{
				imgs[ resolution ] = N5Utils.openVolatile( zarrKeyValueReaderBuilder.build(), multiscales.getDatasets()[ resolution ].path );

				if ( queue == null )
					vimgs[ resolution ] = VolatileViews.wrapAsVolatile( imgs[ resolution ] );
				else
					vimgs[ resolution ] = VolatileViews.wrapAsVolatile( imgs[ resolution ], queue );
			}
//		}
//		catch (IOException e) {
//			e.printStackTrace();
//			throw new RuntimeException(e);
//		}

	}

	/**
	 * Read metadata, set image type, but does not initialize images because queue is not yet available
	 */
	private void init()
	{
		if ( multiscales != null ) return;
		zarrKeyValueReaderBuilder.initS3Client();
		try (final ZarrKeyValueReader zarrKeyValueReader = zarrKeyValueReaderBuilder.build())
		{
			// Fetch metadata
			//
			Multiscales[] multiscalesArray = zarrKeyValueReader.getAttribute( "", MULTI_SCALE_KEY, Multiscales[].class );

			// In principle the call above would be sufficient.
			// However since we need to support different
			// versions of OME-Zarrr we need to "manually"
			// fix some fields.
			// Thus, we parse the same JSON again and fill in missing
			// information.
			// TODO: could we do this by means of a JsonDeserializer?

			final JsonArray multiscalesJsonArray;
			multiscalesJsonArray = zarrKeyValueReader.getAttributes( "" ).getAsJsonObject().get( MULTI_SCALE_KEY ).getAsJsonArray();
			for ( int i = 0; i < multiscalesArray.length; i++ )
			{
				multiscalesArray[ i ].applyVersionFixes( multiscalesJsonArray.get( i ).getAsJsonObject() );
				multiscalesArray[ i ].init();
			}

			// TODO
			//   From the spec:
			//   "If only one multiscale is provided, use it.
			//   Otherwise, the user can choose by name,
			//   using the first multiscale as a fallback"
			//   Right now, we always only use the first one.
			//   One option would be to add the {@code multiscaleArrayIndex}
			//   array index as a parameter to the constructor
			multiscales = multiscalesArray[ multiscaleArrayIndex ];

			// Here, datasets are single resolution N-D Images.
			// Each dataset represents one resolution layer.
			final Multiscales.Dataset[] datasets = multiscales.getDatasets();
			numResolutions = datasets.length;

			// Set the dimensions and data type
			// from the highest resolution dataset's
			// metadata.

			multiDimensions = new long[numResolutions][];
			multiDataType = new DataType[numResolutions];

			for (int resolution = numResolutions-1; resolution >= 0; --resolution) {
				final DatasetAttributes attributes = zarrKeyValueReader.getDatasetAttributes(datasets[resolution].path);
				// n5-zarr provides dimensions in the java order
				multiDimensions[resolution] = attributes.getDimensions();
				multiDataType[resolution] = attributes.getDataType();
			}
			dimensions = multiDimensions[0];
			dataType = multiDataType[0];
			initTypes( dataType );
		}
		catch ( Exception e )
		{
			e.printStackTrace();
			throw new RuntimeException( e );
		}
	}

	private void initTypes( DataType dataType )
	{
		if ( type != null ) return;

		// TODO JOHN: Does the below code already exists
		//   somewhere in N5?
		switch ( dataType ) {
			case UINT8:
				type = Cast.unchecked( new UnsignedByteType() );
				break;
			case UINT16:
				type = Cast.unchecked( new UnsignedShortType() );
				break;
			case UINT32:
				type = Cast.unchecked( new UnsignedIntType() );
				break;
			case UINT64:
				type = Cast.unchecked( new UnsignedLongType() );
				break;
			case INT8:
				type = Cast.unchecked( new ByteType() );
				break;
			case INT16:
				type = Cast.unchecked( new ShortType() );
				break;
			case INT32:
				type = Cast.unchecked( new IntType() );
				break;
			case INT64:
				type = Cast.unchecked( new LongType() );
				break;
			case FLOAT32:
				type = Cast.unchecked( new FloatType() );
				break;
			case FLOAT64:
				type = Cast.unchecked( new DoubleType() );
				break;
		}

		volatileType = ( V ) VolatileTypeMatcher.getVolatileTypeForType( type );
	}

	public Multiscales getMultiscales()
	{
		return multiscales;
	}

	public long[] dimensions()
	{
		init();
		return dimensions;
	}

	public int numResolutions()
	{
		init();
		return numResolutions;
	}

	public CachedCellImg< T, ? > getImg( final int resolutionLevel )
	{
		init();
		initImages();
		return imgs[ resolutionLevel ];
	}

	public long[] getDimensions(final int level)
	{
		return multiDimensions[level];
	}

	public RandomAccessibleInterval< V > getVolatileImg( final int resolutionLevel )
	{
		init();
		initImages();
		return vimgs[ resolutionLevel ];
	}

	public DataType getDataType()
	{
		return dataType;
	}

	public T getType()
	{
		init();
		return type;
	}

	public V getVolatileType()
	{
		init();
		return volatileType;
	}

	public SharedQueue getSharedQueue()
	{
		return queue;
	}

	public void setSharedQueue(SharedQueue sharedQueue)
	{
		queue = sharedQueue;
	}

	public int numDimensions()
	{
		return dimensions.length;
	}

	public static void main( String[] args ) throws IOException {
//		final String multiscalePath = "/home/gabor.kovacs/data/davidf_sample_dataset/SmartSPIM_617052_sample.zarr";
//		final MultiscaleImage< ?, ? > multiscaleImage = new MultiscaleImage<>(new ZarrKeyValueReaderBuilder(multiscalePath));
//		System.out.println(Arrays.toString(multiscaleImage.dimensions()));
//		final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_WEST_2).build();
		final AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new AnonymousAWSCredentials());
//		final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
//				.withCredentials(credentialsProvider)
//				.withRegion(Regions.US_WEST_2)
//				.build();
		final MultiscaleImage<?, ?> multiscaleImage = new MultiscaleImage<>(
				new ZarrKeyValueReaderBuilder(AmazonS3ClientBuilder.standard()
						.withCredentials(credentialsProvider)
						.withRegion(Regions.US_WEST_2).build(), "aind-open-data",
						"/exaSPIM_653431_2023-05-06_10-23-15/exaSPIM.zarr/tile_x_0000_y_0000_z_0000_ch_488.zarr/"));
		System.out.println(Arrays.toString(multiscaleImage.dimensions()));

//		S3Object myobj = s3.getObject("aind-open-data","exaSPIM_653431_2023-05-06_10-23-15/exaSPIM.zarr/tile_x_0000_y_0000_z_0000_ch_488"
//						+".zarr/.zattrs");
//		S3ObjectInputStream inputStream = myobj.getObjectContent();
//		String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
//		System.out.println(result);
	}
}
