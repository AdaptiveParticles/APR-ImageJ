package AprViewer;

import bdv.spimdata.SequenceDescriptionMinimal;
import bdv.spimdata.SpimDataMinimal;
import bdv.util.Bdv;
import bdv.util.BdvFunctions;
import mosaic.JavaAPR;
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
@Plugin( type = Command.class, menuPath = "Plugins > APR > APR (BDV) viewer" )
public class AprViewerCommand implements Command
{
	@Parameter
	File file;

	@Override
	public void run()
	{
		JavaAPR apr = new JavaAPR();
		System.out.println( "Loading [" + file.getPath() + "]" );
		apr.read( file.getPath() );
		System.out.println( "APR-ImageJ: Loaded image size (w/h/d): " + apr.width() + "/" + apr.height() + "/" + apr.depth() );

		final File basePath = file.getParentFile();

		final HashMap< Integer, TimePoint > timepointMap = new HashMap<>();
		final int timepointId = 0;
		timepointMap.put( timepointId, new TimePoint( timepointId ) );
		final HashMap< Integer, BasicViewSetup> setupMap = new HashMap<>();
		final int setupId = 0;
		setupMap.put( setupId, new BasicViewSetup( setupId, "APR", null, null ) );
		final int[] cellDimensions = new int[] { 32, 32, 32 };
		final int numLevels = 6;
		final APRImgLoader imgLoader = new APRImgLoader( apr, cellDimensions, numLevels);
		SequenceDescriptionMinimal seq = new SequenceDescriptionMinimal( new TimePoints( timepointMap ), setupMap, imgLoader, null );

		final HashMap<ViewId, ViewRegistration> registrations = new HashMap<>();
		final AffineTransform3D calibration = new AffineTransform3D();
		calibration.set(
				1, 0, 0, 0,
				0, 1, 0, 0,
				0, 0, 1, 0 );
		registrations.put( new ViewId( timepointId, setupId ), new ViewRegistration(timepointId, setupId, calibration) );

		final SpimDataMinimal spimData = new SpimDataMinimal( basePath, seq, new ViewRegistrations(registrations) );

		// ------------ Run BDV ----------------------------------
		BdvFunctions.show(spimData, Bdv.options().frameTitle("APR viewer [" + file.getName() + "]"));
    }

	public static void main(String... args) {
        ImageJ ij = new ImageJ();

        File file = new File("/Volumes/GONCIARZ/AprFiles/new/zebra7GB.h5");
        ij.command().run(AprViewerCommand.class, true, "file", file);
        ij.ui().showUI();
	}
}
