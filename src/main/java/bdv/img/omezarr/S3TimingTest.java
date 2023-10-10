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

import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.AnonymousAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.janelia.saalfeldlab.n5.s3.AmazonS3KeyValueAccess;

public class S3TimingTest {
    public static void main(String[] args) throws IOException {
        final String bucketName = "aind-open-data";
        final String path = "exaSPIM_653431_2023-05-06_10-23-15/exaSPIM.zarr/tile_x_0000_y_0000_z_0000_ch_488.zarr";
        long t0 = System.currentTimeMillis();

//        final AWSStaticCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new AnonymousAWSCredentials());
//        final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
//                .withCredentials(credentialsProvider)
//                .withRegion(Regions.US_WEST_2)
//                .build();


        // Try with leaving the default credentials provider chain in place
        AWSCredentials credentials = null;
        final AWSStaticCredentialsProvider credentialsProvider;
        try {
            credentials = new DefaultAWSCredentialsProviderChain().getCredentials();
        }
        catch(final Exception e) {
            System.out.println( "Could not load AWS credentials, falling back to anonymous." );
        }
        credentialsProvider = new AWSStaticCredentialsProvider(credentials == null ? new AnonymousAWSCredentials() : credentials);
        final AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion(Regions.US_WEST_2)
                .build();
        final AmazonS3KeyValueAccess kva = new AmazonS3KeyValueAccess(s3, bucketName, false);

        long t1 = System.currentTimeMillis();
        System.out.println("creating KeyValueAccess took: " + (t1 - t0) + " ms");

        System.out.println("kva.isDirectory(path + \"/\") = " + kva.isDirectory(path + "/"));
        long t2 = System.currentTimeMillis();
        System.out.println("took: " + (t2 - t1) + " ms");

        System.out.println("kva.isFile(path + \"/.zattrs\") = " + kva.isFile(path + "/.zattrs"));
        long t3 = System.currentTimeMillis();
        System.out.println("took: " + (t3 - t2) + " ms");

        final InputStream inputStream = kva.lockForReading(path + "/.zattrs").newInputStream();
        String result = new BufferedReader(new InputStreamReader(inputStream))
                .lines().collect(Collectors.joining("\n"));

        long t4 = System.currentTimeMillis();
// System.out.println("result = " + result);
        System.out.println("reading .zattrs took: " + (t4 - t3) + " ms");

    }
}

