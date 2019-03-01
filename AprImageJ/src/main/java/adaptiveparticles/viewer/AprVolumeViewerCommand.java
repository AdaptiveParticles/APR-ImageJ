package adaptiveparticles.viewer;

import adaptiveparticles.apr.AprBasicOps;
import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import mpicbg.spim.data.SpimDataException;
import mpicbg.spim.data.generic.sequence.BasicViewSetup;
import mpicbg.spim.data.registration.ViewRegistration;
import mpicbg.spim.data.registration.ViewRegistrations;
import mpicbg.spim.data.sequence.TimePoint;
import mpicbg.spim.data.sequence.TimePoints;
import mpicbg.spim.data.sequence.ViewId;
import net.imagej.ImageJ;
import net.imglib2.realtransform.AffineTransform3D;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.util.HashMap;

/**
 * The import APR menu entry.
 */
@Plugin( type = Command.class, menuPath = "Plugins > APR > APR (bigdatavolume) viewer" )
public class AprVolumeViewerCommand implements Command
{
	@Parameter
	File file;

	@Override
	public void run()
	{
		// ------------ Load APR ---------------------------------
		final AprBasicOps apr = new AprBasicOps();
		System.out.println( "Loading [" + file.getPath() + "]" );
		apr.read( file.getPath() );
		System.out.println( "Loaded image size (w/h/d): " + apr.width() + "/" + apr.height() + "/" + apr.depth() );

		// ------------ Set BDV stuff ----------------------------
		final File basePath = file.getParentFile();

		final HashMap< Integer, TimePoint > timepointMap = new HashMap<>();
		final int timepointId = 0;
		timepointMap.put( timepointId, new TimePoint( timepointId ) );
		final HashMap< Integer, BasicViewSetup> setupMap = new HashMap<>();
		final int setupId = 0;
		setupMap.put( setupId, new BasicViewSetup( setupId, "APR", null, null ) );
		final int[] cellDimensions = new int[] { 32, 32, 32 };
		final int numLevels = 3;
		final APRImgLoader imgLoader = new APRImgLoader( apr, cellDimensions, numLevels);
		final SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepointMap ), setupMap, imgLoader, null );

		final HashMap<ViewId, ViewRegistration> registrations = new HashMap<>();
		final AffineTransform3D calibration = new AffineTransform3D();
		calibration.set(
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, 0 );
		registrations.put( new ViewId( timepointId, setupId ), new ViewRegistration(timepointId, setupId, calibration) );

		final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seq, new ViewRegistrations(registrations) );

		final int windowWidth = 640;
		final int windowHeight = 480;
		final int renderWidth = 640;
		final int renderHeight = 480;
		final int ditherWidth = 3;
		final int numDitherSamples = 8;
		final int cacheBlockSize = 32;
		final int maxCacheSizeInMB = 300;
		final double dCam = 2000;
		final double dClip = 1000;

		try {
			AprVolumeRenderer.run(spimData, windowWidth, windowHeight, renderWidth, renderHeight, ditherWidth, numDitherSamples, cacheBlockSize, maxCacheSizeInMB, dCam, dClip);
		} catch (SpimDataException e) {
			e.printStackTrace();
		}
    }

	public static void main(String... args) {
        ImageJ ij = new ImageJ();

        File file = new File("/Volumes/GONCIARZ/AprFiles/new/zebra.h5");
        ij.command().run(AprVolumeViewerCommand.class, true, "file", file);
        ij.ui().showUI();
	}
}
