[![Build Status](https://github.com/bigdataviewer/bigdataviewer-omezarr/actions/workflows/build.yml/badge.svg)](https://github.com/bigdataviewer/bigdataviewer-omezarr/actions/workflows/build.yml)

# bigdataviewer-omezarr

Building and Installation
-------------------------

`bigdataviewer-omezarr` releases are available via the `AllenNeuralDynamics` (unlisted) Fiji update site. 
In Fiji, go to Update... -> Advanced mode -> Manage Update Sites -> Add Unlisted Site. Enter name `AllenNeuralDynamics` and URL `https://sites.imagej.net/AllenNeuralDynamics/`. After refreshing, `jars/bigdataviewer-omezarr.jar` will be available for installation.

For more information on Fiji update sites, please visit the [imagej documentation](https://imagej.net/update-sites/setup).

Alternatively, the git repository checkout can be built by maven:
```shell
mvn clean install
```
then copy the compiled snapshot version jar (e.g. `bigdataviewer-omezarr-0.2.2-SNAPSHOT.jar`)
from the local `target/` folder into Fiji's `jars/` folder.

OME-Zarr support
----------------

This package provides OME-Zarr reading support to bigdataviewer and BigStitcher. In addition to the OME-NGFF json metadata, a
bigdataviewer `dataset.xml` dataset definition is required that refers to the image format `bdv.multimg.zarr`. Currently, 
two basic loader classes are provided: `XmlIoZarrImageLoader` and `ZarrImageLoader`.

The OME-NGFF layouts supported by this package is limited. Most notably:

* Only OME-NGFF v0.4. is supported.

* Only full, 5 dimensional OME-Zarr (t,ch,z,y,x axes) images are supported.

* In bigdataviewer the image is reduced to 3 dimensions at `t=0`, `ch=0`.

* In case of multiple images, all images in one  `ViewSetup` must have the same
  data type and the same resolution levels.

* `unit` in `axes` definitions in `.zattrs` are ignored. The same units are implicitly assumed across images. Use 
  `voxelSize` in `dataset.xml` to define physical units.

* For multi-resolution images, the first dataset defined in the `.zattrs` must be the raw (finest) resolution. Anisotropy
  defined for the raw resolution in `.zattrs` are ignored, use `voxelSize` in `dataset.xml` instead. Downsampling factors
  are determined from `coordinateTransformations` compared to the raw resolution. Only factor of 2 downsampling sequences 
  have been tested.

* Multiple images must be located in separate zgroup folders. Only one image per top level `.zattrs` file is allowed
  (multiple entries in the `multiscales` section are disregarded).

bdv.multimg.zarr format
-----------------------

The `dataset.xml` file should define the location of the zarr image for each _view_ in the following format.

### Local filesystem access


```xml
<ImageLoader format="bdv.multimg.zarr" version="1.0">
  <!-- type="relative" invokes path resolution that is relative to the xml file location itself. -->
  <zarr type="absolute">/absolute_path_to_zarr_root</zarr>
  <zgroups>
   <zgroup setup="0" timepoint="0">
     <path>relative_path_from_zarr_root</path>
   </zgroup>
   ...
  </zgroups>
</ImageLoader>
```

### Direct S3 access

Direct AWS S3 access is also supported. It is triggered by the presence of the `<s3bucket>` tag in the xml:

```xml
<ImageLoader format="bdv.multimg.zarr" version="1.0">
  <s3bucket>aind-open-data</s3bucket>
  <!-- Use type=absolute and never start with a leading slash. -->
  <zarr type="absolute">prefix_to_zarr_root_within_s3_bucket</zarr>
  <zgroups>
   <zgroup setup="0" timepoint="0">
     <path>relative_path_from_zarr_root</path>
   </zgroup>
   ...
  </zgroups>
</ImageLoader>
```

Note: The xml file itself still must be a local filesystem file.

S3 credentials must be accessible to the default AWS authentication chain. In
most cases, credentials may be set in environment variables. While there is a fallback to try anonymous access if none
is provided, authentication is usually faster even to open data buckets with explicit credentials.

Also, `AWS_REGION` and `AWS_DEFAULT_REGION` must be set.

```
export AWS_REGION=us-west-2
export AWS_DEFAULT_REGION=us-west-2
```

Acknowledgement
---------------

This package uses [saalfeldlab/n5-zarr](https://github.com/saalfeldlab/n5-zarr) for the actual data transfer.
The `Multiscales` class is copied from the [mobie/mobie-io hackathon_prague_2022](https://github.com/mobie/mobie-io/tree/hackathon_prague_2022) branch.
