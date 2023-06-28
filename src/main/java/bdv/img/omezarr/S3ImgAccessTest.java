package bdv.img.omezarr;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.google.gson.GsonBuilder;
import org.janelia.saalfeldlab.n5.s3.AmazonS3KeyValueAccess;
import org.janelia.saalfeldlab.n5.zarr.ZarrKeyValueReader;

import java.io.IOException;

public class S3ImgAccessTest {
    public static void main(String[] args) throws IOException {
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard().withRegion(Regions.US_WEST_2).build();

        System.out.println("");
        final AmazonS3KeyValueAccess mys3 = new AmazonS3KeyValueAccess(s3, "aind-open-data", false);
        final ZarrKeyValueReader zreader = new ZarrKeyValueReader(mys3,
                "/exaSPIM_653431_2023-05-06_10-23-15/exaSPIM.zarr/tile_x_0000_y_0000_z_0000_ch_488.zarr/",
                new GsonBuilder(), true, true, true);
        zreader.close();
    }

}