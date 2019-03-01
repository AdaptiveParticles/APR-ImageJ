# APR-ImageJ
APR-ImageJ provides support for [Adaptive Particle Representation](https://github.com/AdaptiveParticles/LibAPR) (APR) in ImageJ/Fiji.

## Available commands
#### File -> Import -> APR...
Imports APR file to ImageJ. It reconstructs whole image from APR representation to pixel based (standard ImageJ stack).
 
#### File -> Export -> APR..
Exports pixel based image to APR format. 
Parameters:
- Intensity Threshold - values below this threshold will be ignored, this is useful for removing camera artifacts
- Minimal SNR - minimal ratio of the signal to the standard deviation of the background
- Lambda - gradient smoothing parameter (reasonable range 0.1-10)
- Minimum signal - minimum absolute signal size relative to the local background, useful for removing background 
- Relative error - maximum reconstruction error relative to local intensity scale (reasonable range 0.08-0.15)

*Note1: set value of parameter(s) to -1 for automatic detection of best possible values.*

*Note2: extension ```_apr.h5``` is added to exported file name.*

#### Plugins > APR > APR (BDV) viewer
Opens APR image in [BigDataViewer](https://github.com/bigdataviewer/bigdataviewer-vistools) using [LibAPR-java-wrapper](https://github.com/krzysg/LibAPR-java-wrapper) to show Adaptive Particle Representation (APR) files. Thanks to reconstruction 'on the fly' it allows to open bigger files that memory available on the machine.

#### Plugins > APR > APR (bigdatavolume) viewer
Opens APR image as a volume. This command is based on [BigVolumeViewer](https://github.com/tpietzsch/jogl-minimal).

*Note: this plugin requires BigVolumeViewer already installed in your ImageJ. This can be done by Help->Update->Manage update sites and adding there ```BigVolumeViewer Demo``` to your active updtee sites.* 

![Example of APR volume viewer](AprImageJ/src/main/resources/AprVolumeVierwer.png?raw=true)

## How to download, build and install
#### Download source code of APR-ImageJ
```
git clone --recurse https://github.com/AdaptiveParticles/APR-ImageJ.git
```
#### Build plugin (might take a while)
```
cd APR-ImageJ
mvn package -Dmaven.test.skip=true -Dmaven.javadoc.skip=true
```
#### Manual installation in ImageJ/Fiji
- copy result jar file from ```./AprImageJ/target/AprImageJ-0.0.1-SNAPSHOT_Full.jar``` 
   to plugins directory of your ImageJ installation, on Mac/Linux:
```
cp ./AprImageJ/target/AprImageJ-0.0.1-SNAPSHOT_Full.jar   /my/app/dir/Fiji.app/plugins
```
- also make sure that you have ```javacpp-1.4.1.jar``` in jars directory (can be found in .m2 directory of your home),
   it is needed since javacpp shipped with Fiji is too old:
```
cp ~/.m2/repository/org/bytedeco/javacpp/1.4.1/javacpp-1.4.1.jar /my/app/dir/Fiji.app/jars/
```

## Contact us

If anything is not working as you think it should, or would like it to, please get in touch with us!!!

[![Join the chat at https://gitter.im/LibAPR](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/LibAPR/Lobby?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
