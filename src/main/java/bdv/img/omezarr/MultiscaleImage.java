/*-
 * #%L
 * This package provides multi image OME-Zarr support in bigdataviewer.
 * %%
 * Copyright (C) 2022 - 2024 BigDataViewer developers.
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
import org.janelia.saalfeldlab.n5.*;
import org.janelia.saalfeldlab.n5.imglib2.N5Utils;
import org.janelia.saalfeldlab.n5.s3.AmazonS3KeyValueAccess;
import org.janelia.saalfeldlab.n5.universe.N5Factory;
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

	private final N5Reader n5Reader;

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

	private String multiscalePath; // Our own root path within the n5reader
	/**
	 * TODO
	 */

//	public MultiscaleImage(
//			final ZarrKeyValueReaderBuilder keyValueReaderBuilder)
//	{
//		this.zarrKeyValueReaderBuilder=keyValueReaderBuilder;
//	}

	public MultiscaleImage(
			final N5Reader n5Reader, final String multiscalePath)
	{
		this.n5Reader = n5Reader;
		this.multiscalePath = multiscalePath; // No slash at the end
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
		try {
			// TODO only on demand
			// See N5ImageLoader.prepareCachedImage
			imgs = new CachedCellImg[ numResolutions ];
			vimgs = new RandomAccessibleInterval[ numResolutions ];

			for ( int resolution = 0; resolution < numResolutions; ++resolution )
			{
				imgs[ resolution ] = N5Utils.openVolatile( n5Reader, multiscalePath + "/" + multiscales.getDatasets()[ resolution ].path );

				if ( queue == null )
					vimgs[ resolution ] = VolatileViews.wrapAsVolatile( imgs[ resolution ] );
				else
					vimgs[ resolution ] = VolatileViews.wrapAsVolatile( imgs[ resolution ], queue );
			}
		}
		catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	/**
	 * Read metadata, set image type, but does not initialize images because queue is not yet available
	 */
	private void init()
	{
		if ( multiscales != null ) return;
//		zarrKeyValueReaderBuilder.initS3Client();
		try
		{
			// Fetch metadata
			//
			Multiscales[] multiscalesArray = n5Reader.getAttribute( multiscalePath, MULTI_SCALE_KEY, Multiscales[].class );
			// In principle the call above would be sufficient.
			// However since we need to support different
			// versions of OME-Zarrr we need to "manually"
			// fix some fields.
			// Thus, we parse the same JSON again and fill in missing
			// information.
			// TODO: could we do this by means of a JsonDeserializer?

//			final JsonArray multiscalesJsonArray;
//			multiscalesJsonArray = n5Reader.getAttributes( "" ).getAsJsonObject().get( MULTI_SCALE_KEY ).getAsJsonArray();
//			for ( int i = 0; i < multiscalesArray.length; i++ )
//			{
//				multiscalesArray[ i ].applyVersionFixes( multiscalesJsonArray.get( i ).getAsJsonObject() );
//				multiscalesArray[ i ].init();
//			}

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
				final DatasetAttributes attributes = n5Reader.getDatasetAttributes(multiscalePath + "/" + datasets[resolution].path);
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
//		final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
//				.withCredentials(credentialsProvider)
//				.withRegion(Regions.US_WEST_2)
//				.build();
//		final MultiscaleImage<?, ?> multiscaleImage = new MultiscaleImage<>(
//				new ZarrKeyValueReaderBuilder(AmazonS3ClientBuilder.standard()
//						.withCredentials(credentialsProvider)
//						.withRegion(Regions.US_WEST_2).build(), "aind-open-data",
//						"/exaSPIM_653431_2023-05-06_10-23-15/exaSPIM.zarr/tile_x_0000_y_0000_z_0000_ch_488.zarr/"));
//
//		final String s3Region = new DefaultAwsRegionProviderChain().getRegion();
		final N5Reader n5r = new N5Factory().s3UseCredentials().openReader(
				N5Factory.StorageFormat.ZARR,  "s3://aind-open-data/exaSPIM_653431_2023-05-06_10-23-15/exaSPIM.zarr");
		final MultiscaleImage<?, ?> multiscaleImage = new MultiscaleImage<>(n5r,
				"tile_x_0000_y_0000_z_0000_ch_488.zarr/");
		System.out.println(Arrays.toString(multiscaleImage.dimensions()));
		System.out.println(multiscaleImage.numResolutions());
		for (int i=0; i<multiscaleImage.numResolutions(); i++)
		{
		 			System.out.println(Arrays.toString(multiscaleImage.getDimensions(i)));
		}
	}
}
