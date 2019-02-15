# APR-ImageJ

Simple and initial implementation of APR files support in ImageJ/Fiji.
After building and installation in ImageJ two new commands are available:

* File -> Import -> APR...
* Plugins > APR > APR (BDV) viewer

First one imports APR file to ImageJ stack which can be later used with any ImageJ algorithm/command (currently quite slow and does not support very large files)
The other one opens APR image in [BigDataViewer](https://github.com/bigdataviewer/bigdataviewer-vistools) using [LibAPR-java-wrapper](https://github.com/krzysg/LibAPR-java-wrapper) to show Adaptive Particle Representation (APR) files. Thanks to reconstruction 'on the fly' it allows to open bigger files that memory available on the machine.

## How to download, build and install
* clone repository
```
git clone --recurse https://github.com/AdaptiveParticles/APR-ImageJ.git
```
* build java app
```
cd APR-ImageJ
mvn pakcage -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```
* install in ImageJ/Fiji
copy result jar file from ./AprImageJ/target/AprImageJ-jar-with-dependencies.jar to your ImageJ plugins directory

## Contact us

If anything is not working as you think it should, or would like it to, please get in touch with us!!!

[![Join the chat at https://gitter.im/LibAPR](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/LibAPR/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
