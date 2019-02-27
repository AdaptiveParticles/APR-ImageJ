package AdaptiveParticles.AprFullImageLoader;

import AdaptiveParticles.JavaAPR;
import net.imagej.ImageJ;
import org.scijava.ItemIO;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;

/**
 * The import APR menu entry.
 */
@Plugin( type = Command.class, menuPath = "File > Import > APR..." )
public class OpenAprCommand implements Command
{
	@Parameter(style = "file")
	File file;

	@Parameter(type = ItemIO.OUTPUT)
    AprImg output;

	@Override
	public void run() {
        JavaAPR apr = new JavaAPR();
        apr.read( file.getPath() );
        System.out.println( "Loaded image size (w/h/d): " + apr.width() + "/" + apr.height() + "/" + apr.depth() );
        apr.reconstruct();

        output = AprImg.of(apr);
	}

    /**
     * main method to be used for debugging
     */
	public static void main(String... args) {
		ImageJ ij = new ImageJ();

        File file = new File("/Volumes/GONCIARZ/sphere_apr.h5");
        ij.command().run(OpenAprCommand.class, true, "file", file);
        ij.ui().showUI();
	}
}
