[![Build Status](https://github.com/bigdataviewer/bigdataviewer-omezarr/actions/workflows/build.yml/badge.svg)](https://github.com/bigdataviewer/bigdataviewer-omezarr/actions/workflows/build.yml)

# bigdataviewer-omezarr

OME-Zarr support
----------------

This package provides OME-Zarr reading support to bigdataviewer. In addition to the OME-NGFF json metadata, a
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

* For multiresolution images, the first dataset defined in the `.zattrs` must be the raw (finest) resolution. Anisotropy
  defined for the raw resolution in `.zattrs` are ignored, use `voxelSize` in `dataset.xml` instead. Downsampling factors
  are determined from `coordinateTransformations` compared to the raw resolution.

* Multiple images must be located in separate zgroup folders. Only one image per top level `.zattrs` file allowed
  (multiple entries in the `multiscales` section are disregarded).

bdv.multimg.zarr format
-----------------------

The `dataset.xml` file should define the location of the zarr image for each _view_ in the following format:

```xml
<ImageLoader format="bdv.multimg.zarr" version="1.0">
  <zarr type="absolute">/absolute_path_to_zarr_root</zarr>
  <zgroups>
   <zgroup setup="0" timepoint="0">
     <path>relative_path_from_zarr_root</path>
   </zgroup>
   ...
  </zgroups>
</ImageLoader>
```

Build instructions
------------------

Build and install:

```shell
mvn clean install
```

Acknowledgement
---------------

This package uses [saalfeldlab/n5-zarr](https://github.com/saalfeldlab/n5-zarr) for the actual data transfer.
The `Multiscales` class is copied from the [mobie/mobie-io hackathon_prague_2022](https://github.com/mobie/mobie-io/tree/hackathon_prague_2022) branch.
