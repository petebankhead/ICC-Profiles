# Exploring ICC Profiles

This repo explores how to apply [ICC profiles](https://en.wikipedia.org/wiki/ICC_profile) to images in both Python and Java.

It is aimed mostly at pathology applications.

For more info, see @choosehappy's post at http://www.andrewjanowczyk.com/application-of-icc-profiles-to-digital-pathology-images/ - which is also the inspiration for the Python bit.

ICC profiles are known to be a thing with Aperio SVS images, and they are applied by default in the ImageScope viewer - but not applied by default in other common software... such as [QuPath](http://qupath.github.io) (at least [not in the current version 0.3.2](https://forum.image.sc/t/color-discrepancy-qupath-x-imagescope-leica-gt450/57948/2)).
As a result, the colors can appear differently.

The sample images here are from https://openslide.org (CMU-1.svs).
I opened them in ImageScope, then exported them using three approaches:

* Apply the ICC profile
* Embed the ICC profile
* Ignore the ICC profile

The aim is to compare between these, and ideally figure out how to use the embedded ICC Profile to get a similar result to the one where it was applied at export.


## Python

The python code is shown in [ICC Profiles in Python.ipynb](https://github.com/petebankhead/ICC-Profiles/blob/main/ICC%20Profiles%20in%20Python.ipynb).

In summary: reading the 'ignore' and 'embedded' images with `imageio` give the same pixel data; 'applied' is different.

However, the ICC profile can be extracted from the 'embedded' image and converted to a transform using Pillow.
Applying this transform to the 'ignore' or 'embedded' image gets a result that is very similar image to 'applied' (but not identical).


## Java

The Java code is in the `app` directory, and runnable with 

```
gradlew run
```

The full output is shown at the bottom, but the main points are:

* When reading with ImageIO, 'applied' and 'embedded' give similar (not identical) images - *any embedded ICC profile is used while the image is being read*
  * This is **not** necessarily the case when reading the same image with other software, e.g. using Bio-Formats to read the same images doesn't appear to use the embedded ICC profile
* If the the ICC profile is extracted, it can be applied later using a [`ColorConvertOp`](https://docs.oracle.com/en/java/javase/11/docs/api/java.desktop/java/awt/image/ColorConvertOp.html) - *but be careful!*
  * If providing a `BufferedImage` as input, the output image is different from what is expected
  * If providing a `WriteableRaster` as input and constructing a `ColorModel` manually, the image created by combining those *can* be the same as the result given by ImageIO when reading the image with the embedded transform

```java
// Create a color convert op to apply the transform
ICC_Profile iccTarget = ICC_Profile.getInstance(ColorSpace.CS_sRGB);
ColorConvertOp op = new ColorConvertOp(
        new ICC_Profile[] {iccSource, iccTarget},
        null
);

// Try applying the transform to the buffered image
// THIS *DIDN'T* WORK FOR ME!
BufferedImage imgTransformed = op.filter(img, null);

// Try applying the transform to the raster only
// THIS *DID* WORK FOR ME!
BufferedImage imgTransformedRaster =
        new BufferedImage(
                new ComponentColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB), false, false,
                        ColorModel.OPAQUE, DataBuffer.TYPE_BYTE),
                op.filter(img.getRaster(), null),
                false,
                null
        );
```


Basically, it's not straightforward:

1. There's a risk that the ICC profile has already been used, depending upon how the image was read
2. If the ICC profile *wasn't* used, there's a risk that it gives unexpected results because `ColorConvertOp` can give a different outcome depending upon whether it's provided with a `BufferedImage` or a `WritableRaster`


### Java output

```
-----
CMU-Apply.tif and CMU-Embed.tif are different
   Different pixels: 8.5 %
   Mean: -0.0, Min: -3.0, Max: 7.0, MAD: 0.04
-----
CMU-Apply.tif and CMU-Ignore.tif are different
   Different pixels: 99.9 %
   Mean: 3.8, Min: -62.0, Max: 31.0, MAD: 5.16
-----
CMU-Apply.tif and TRANSFORMED are different
   Different pixels: 100.0 %
   Mean: -1.4, Min: -84.0, Max: 32.0, MAD: 8.59
-----
CMU-Apply.tif and TRANSFORMED-RASTER are different
   Different pixels: 8.5 %
   Mean: -0.0, Min: -3.0, Max: 7.0, MAD: 0.04
-----
-----
CMU-Embed.tif and CMU-Ignore.tif are different
   Different pixels: 100.0 %
   Mean: 3.8, Min: -62.0, Max: 32.0, MAD: 5.17
-----
CMU-Embed.tif and TRANSFORMED are different
   Different pixels: 100.0 %
   Mean: -1.4, Min: -84.0, Max: 32.0, MAD: 8.59
-----
CMU-Embed.tif and TRANSFORMED-RASTER are identical
-----
-----
CMU-Ignore.tif and TRANSFORMED are different
   Different pixels: 100.0 %
   Mean: -5.3, Min: -40.0, Max: 41.0, MAD: 9.06
-----
CMU-Ignore.tif and TRANSFORMED-RASTER are different
   Different pixels: 100.0 %
   Mean: -3.8, Min: -32.0, Max: 62.0, MAD: 5.17
-----
-----
TRANSFORMED and TRANSFORMED-RASTER are different
   Different pixels: 100.0 %
   Mean: 1.4, Min: -32.0, Max: 84.0, MAD: 8.59
-----
```
