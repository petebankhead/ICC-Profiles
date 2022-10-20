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