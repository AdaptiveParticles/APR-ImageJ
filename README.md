# APR-ImageJ

Simple and initial implementation of APR support in ImageJ/Fiji.
After building and installation in ImageJ three new commands are available:

* File -> Import -> APR...
* File -> Export -> APR..
* Plugins > APR > APR (BDV) viewer

First one imports APR file to ImageJ stack which can be later used with any ImageJ algorithm/command (currently quite slow and does not support very large files).
Second one exports current pixel image to APR format.
The last one opens APR image in [BigDataViewer](https://github.com/bigdataviewer/bigdataviewer-vistools) using [LibAPR-java-wrapper](https://github.com/krzysg/LibAPR-java-wrapper) to show Adaptive Particle Representation (APR) files. Thanks to reconstruction 'on the fly' it allows to open bigger files that memory available on the machine.

## How to download, build and install
* clone repository
```
git clone --recurse https://github.com/adaptiveparticles/APR-ImageJ.git
```
* build java app
```
cd APR-ImageJ
mvn package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```
* (very) manual installation in ImageJ/Fiji
```
1. copy result jar file from ./AprImageJ/target/AprImageJ-0.0.1-SNAPSHOT_Full.jar 
   to plugins directory of your ImageJ installation, on Mac/Linux:
   
   cp ./AprImageJ/target/AprImageJ-0.0.1-SNAPSHOT_Full.jar   /my/applications/directory/Fiji.app/plugins
   
2. also make sure that you have javacpp-1.4.1.jar in jars directory (can be found in .m2 directory of your home),
   it is needed since javacpp shipped with Fiji is too old:

   cp ~/.m2/repository/org/bytedeco/javacpp/1.4.1/javacpp-1.4.1.jar /my/applications/directory/Fiji.app/jars/
```

## Contact us

If anything is not working as you think it should, or would like it to, please get in touch with us!!!

[![Join the chat at https://gitter.im/LibAPR](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/LibAPR/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
