package AdaptiveParticles.AprFullImageLoader;

import AdaptiveParticles.JavaAPR;
import net.imagej.Dataset;
import net.imagej.ImageJ;
import net.imagej.ImgPlus;
import net.imagej.ops.OpService;
import net.imglib2.Cursor;
import net.imglib2.img.Img;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Simple command for exporting pixel images to APR
 * works only on 16-bit APRs and converts any input image to 16-bit unsigned short
 */
@Plugin( type = Command.class, menuPath = "File > Export > APR.." )
public class SaveAprCommand implements Command
{
    @Parameter
    private ImgPlus iInputImage;

    @Parameter
    OpService ops;

    @Parameter(style = "save")
    File file;

	@Override
	public void run() {
	    // get 16-bit image
        final Img<UnsignedShortType> imgShort = ops.convert().uint16(iInputImage);

        // get dimensions of input image
	    long dims[] = new long[imgShort.numDimensions()];
        imgShort.dimensions(dims);
        System.out.println("Input image dims w/h/d:" + dims[0] + "/" + dims[1] + "/" + dims[2]);

        // allocate output buffer ...
        ByteBuffer bb = ByteBuffer.allocateDirect((int) dims[0] * (int) dims[1] * (int) dims[2] * 2 /*short*/);
        bb.order(ByteOrder.LITTLE_ENDIAN);
        ShortBuffer outputBuffer =  bb.asShortBuffer();

        // ... and copy input image there
        Cursor<UnsignedShortType> cursor = imgShort.cursor();
        long[] pos = new long[cursor.numDimensions()];
        while(cursor.hasNext()) {
            cursor.fwd();
            cursor.localize(pos);
            int v = cursor.get().get();
            outputBuffer.put((int)(pos[0] + pos[1] * dims[0] + pos[2] * dims[0] * dims[1]), (short)v);
        }

        // get APR stuff done and save it
        JavaAPR apr = new JavaAPR();
        apr.get16bitUnsignedAPR((int) dims[0], (int) dims[1], (int) dims[2], 16, outputBuffer);
        apr.saveAPR(file.getParent() + "/", file.getName());
        System.out.println("DONE.");
    }

    /**
     * main method to be used for debugging
     */
	public static void main(String... args) throws Exception {
	    ImageJ ij = new ImageJ();

	    // open input image and show it
        final Dataset dataset = ij.scifio().datasetIO().open("/Users/gonciarz/gradient.tif");
        ij.ui().show(dataset);

        // Convert image to APR and save (note: APR lib adds _apr.h5 suffix so provide only prefix of file name)
        ij.command().run(SaveAprCommand.class, true, "iInputImage", dataset, "file", "/Users/gonciarz/outputAPR2").get();

        // Read it back from APR
        ij.command().run(AdaptiveParticles.AprFullImageLoader.OpenAprCommand.class, true, "file", "/Users/gonciarz/outputAPR2_apr.h5");

        ij.ui().showUI();
	}
}
