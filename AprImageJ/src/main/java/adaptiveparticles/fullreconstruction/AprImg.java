package adaptiveparticles.fullreconstruction;

import adaptiveparticles.apr.AprBasicOps;
import net.imglib2.Cursor;
import net.imglib2.cache.img.CellLoader;
import net.imglib2.cache.img.DiskCachedCellImgFactory;
import net.imglib2.cache.img.DiskCachedCellImgOptions;
import net.imglib2.cache.img.SingleCellArrayImg;
import net.imglib2.img.Img;
import net.imglib2.img.WrappedImg;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import org.bytedeco.javacpp.ShortPointer;

import static net.imglib2.cache.img.DiskCachedCellImgOptions.options;

/**
 * This is intended to be "The data type to represent an APR" in ImageJ
 */
public class AprImg extends ForwardingImg< UnsignedShortType > implements WrappedImg< UnsignedShortType >, AutoCloseable
{
	static public class FullImageLoader {
		private ShortPointer iImgData;
		private int iWidth, iHeight, iDepth;

		public FullImageLoader(ShortPointer imgData, int width, int height, int depth) {
			iImgData = imgData;
			iWidth = width;
			iHeight = height;
			iDepth = depth;
		}

		public class ImgLoader implements CellLoader<UnsignedShortType> {
			@Override
			public void load(final SingleCellArrayImg<UnsignedShortType, ?> cell) {
				Cursor<UnsignedShortType> cursor = cell.cursor();
				long[] pos = new long[cursor.numDimensions()];

				// copy iImgData from LibAPR to BDV for current cell
				while (cursor.hasNext()) {
					cursor.fwd();
					cursor.localize(pos);
					cursor.get().set(iImgData.get(pos[0] + pos[1] * iWidth + pos[2] * iWidth * iHeight));
				}
			}
		}

		public Img<UnsignedShortType> getImg() {
		    // Limit size of single cell to 512MB - arbitrary choice but seems to be good enough
		    long xysize = (long) iWidth * (long) iHeight;
		    int maxZsize = (int)(512L * 1024L * 1024L / xysize / 2 /* since short type */);
		    maxZsize = Math.min(maxZsize, iDepth); // Don't create cell bigger than image
		    maxZsize = Math.max(maxZsize, 1); // just in case if image is very small


			final int[] cellDimensions = new int[] {iWidth, iHeight, maxZsize};
			final long[] dimensions = new long[] {iWidth, iHeight, iDepth};

			final DiskCachedCellImgOptions options = options().cellDimensions(cellDimensions);
			final CellLoader<UnsignedShortType> loader = new ImgLoader();
			return new DiskCachedCellImgFactory<>(new UnsignedShortType(), options).create(dimensions, loader);
		}
	}

	private final Img<UnsignedShortType> iImg;
	private final AprBasicOps iAPR;

	private AprImg(Img<  UnsignedShortType > img, AprBasicOps apr) {
		super(img);
		iImg = img;
		iAPR = apr;
	}

	public static AprImg of(AprBasicOps apr) {
		FullImageLoader imgLoader = new FullImageLoader(apr.data(), apr.width(), apr.height(), apr.depth());
		Img<UnsignedShortType> img = imgLoader.getImg();
		return new AprImg(img, apr);
	}

	public Img< UnsignedShortType> getImg() {
		return iImg;
	}

	@Override
	public void close() {
		iAPR.close();
	}
}
