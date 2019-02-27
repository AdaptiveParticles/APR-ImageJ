package AdaptiveParticles.AprViewer;

/**
 * APR Image Loader - supports only unsigned short type
 * Copyright (C) 2018
 * Krzysztof Gonciarz, Tobias Pietzsch
 */

import bdv.AbstractViewerSetupImgLoader;
import bdv.ViewerImgLoader;
import bdv.ViewerSetupImgLoader;
import bdv.cache.CacheControl;
import bdv.img.cache.CacheArrayLoader;
import bdv.img.cache.VolatileGlobalCellCache;
import bdv.img.hdf5.MipmapInfo;
import bdv.util.MipmapTransforms;
import AdaptiveParticles.JavaAPR;
import mpicbg.spim.data.generic.sequence.ImgLoaderHint;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.cache.volatiles.CacheHints;
import net.imglib2.cache.volatiles.LoadingStrategy;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.img.cell.CellGrid;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class APRImgLoader implements ViewerImgLoader {
    private final VolatileGlobalCellCache cache;
    private final APRSetupImgLoader setupImgLoader;

    public APRImgLoader(final JavaAPR apr, final int[] cellDimensions, final int numLevels ) {

        // ------------ Set dimensions from finiest level up to numLevels -------------------
        final long[][] dimensions = new long[numLevels][];
        dimensions[0] = new long[] { apr.width(), apr.height(), apr.depth() };
        for ( int level = 1; level < numLevels; ++level ) {
            dimensions[level] = new long[]{dimensions[level - 1][0] / 2, dimensions[level - 1][1] / 2, dimensions[level - 1][2] / 2};
        }

        // ------------ Set MipmapInfo ------------------------------------------------------
        final double[][] resolutions = new double[numLevels][];
        final int[][] subdivisions = new int[numLevels][];
        final AffineTransform3D[] transforms = new AffineTransform3D[numLevels];
        for (int level = 0; level < numLevels; ++level) {
            final double s = 1 << level;
            resolutions[level] = new double[] {s, s, s};
            subdivisions[level] = cellDimensions;
            transforms[level] = MipmapTransforms.getMipmapTransformDefault(resolutions[level]);
        }
        final MipmapInfo mipmapInfo = new MipmapInfo(resolutions, transforms, subdivisions);

        // ------------ Create Img Loader ---------------------------------------------------
        final int setupId = 0;
        cache = new VolatileGlobalCellCache(numLevels, 6);
        final APRArrayLoader loader = new APRArrayLoader(apr);
        setupImgLoader = new APRSetupImgLoader(setupId, dimensions, mipmapInfo, cache, loader);
    }

    @Override
    public ViewerSetupImgLoader< ?, ? > getSetupImgLoader(final int setupId) {
        return setupImgLoader;
    }

    @Override
    public CacheControl getCacheControl() {
        return cache;
    }

    private class APRArrayLoader implements CacheArrayLoader< VolatileShortArray > {
        private final JavaAPR apr;
        private final ThreadLocal<ShortBuffer> buffer = ThreadLocal.withInitial(
                () -> ByteBuffer.allocateDirect(2).order(ByteOrder.nativeOrder()).asShortBuffer());

        public APRArrayLoader(final JavaAPR apr)
        {
            this.apr = apr;
        }

        @Override
        public VolatileShortArray loadArray(final int timepoint, final int setup, final int level, final int[] dimensions, final long[] min ) throws InterruptedException {
            final int sizeOfReconstructedPatch = (int) Intervals.numElements(dimensions);

            if (buffer.get().capacity() < sizeOfReconstructedPatch) {
                buffer.set(ByteBuffer.allocateDirect(2 * sizeOfReconstructedPatch).order(ByteOrder.nativeOrder()).asShortBuffer());
            }

            // reconstruct APR starting in the begin of buffer
            final ShortBuffer shortBuffer = buffer.get();
            shortBuffer.rewind();
            apr.reconstructToBuffer(
                    (int) min[0], (int) min[1], (int) min[2],    // (x,y,z) of start reconstruction
                    dimensions[0], dimensions[1], dimensions[2], // (width, height, depth) of reconstruction
                    level, shortBuffer);                         // on which level reconstruction is done and output buffer

            final short[] array = new short[sizeOfReconstructedPatch];
            shortBuffer.get(array, 0, sizeOfReconstructedPatch);
            return new VolatileShortArray(array, true);
        }

        @Override
        public int getBytesPerElement() { return 2; /* only short type supported */ }
    }

    private class APRSetupImgLoader extends AbstractViewerSetupImgLoader< UnsignedShortType, VolatileUnsignedShortType > {
        private final int setupId;
        private final long[][] dimensions;
        private final VolatileGlobalCellCache cache;
        private final CacheArrayLoader< VolatileShortArray > loader;

        // Description of available mipmap levels for the setup. Contains for each mipmap level, the subsampling factors and subdivision block sizes.
        private final MipmapInfo mipmapInfo;

        protected APRSetupImgLoader(final int setupId, final long[][] dimensions, final MipmapInfo mipmapInfo, final VolatileGlobalCellCache cache, final CacheArrayLoader<VolatileShortArray> loader) {
            super(new UnsignedShortType(), new VolatileUnsignedShortType());
            this.setupId = setupId;
            this.dimensions = dimensions;
            this.mipmapInfo = mipmapInfo;
            this.cache = cache;
            this.loader = loader;
        }

        @Override
        public RandomAccessibleInterval< UnsignedShortType > getImage(final int timepointId, final int level, final ImgLoaderHint... hints) {
            return prepareCachedImage( timepointId, level, LoadingStrategy.BLOCKING, type );
        }

        @Override
        public RandomAccessibleInterval< VolatileUnsignedShortType > getVolatileImage( final int timepointId, final int level, final ImgLoaderHint... hints ) {
            return prepareCachedImage( timepointId, level, LoadingStrategy.BUDGETED, volatileType );
        }

        protected < T extends NativeType<T>> RandomAccessibleInterval<T> prepareCachedImage(final int timepointId, final int level, final LoadingStrategy loadingStrategy, final T type ) {
            final CellGrid grid = new CellGrid( dimensions[ level ], mipmapInfo.getSubdivisions()[ level ] );
            final int priority = mipmapInfo.getMaxLevel() - level;
            final CacheHints cacheHints = new CacheHints( loadingStrategy, priority, false );
            return cache.createImg( grid, timepointId, setupId, level, cacheHints, loader, type );
        }

        @Override
        public double[][] getMipmapResolutions() { return mipmapInfo.getResolutions(); }

        @Override
        public AffineTransform3D[] getMipmapTransforms() { return mipmapInfo.getTransforms(); }

        @Override
        public int numMipmapLevels() { return mipmapInfo.getNumLevels(); }
    }
}
