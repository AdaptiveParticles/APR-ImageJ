package adaptiveparticles.viewer;

/**
 * APR Volume Viewer
 *
 * Copyright (C) 2018
 * Tobias Pietzsch
 * Krzysztof Gonciarz (adapted from Example9 from jogl-minimal by Tobias)
 */

import bdv.cache.CacheControl;
import bdv.spimdata.SpimDataMinimal;
import bdv.tools.InitializeViewerState;
import bdv.tools.ToggleDialogAction;
import bdv.tools.VisibilityAndGroupingDialog;
import bdv.tools.brightness.BrightnessDialog;
import bdv.tools.brightness.ConverterSetup;
import bdv.tools.brightness.MinMaxGroup;
import bdv.tools.brightness.SetupAssignments;
import bdv.tools.transformation.ManualTransformation;
import bdv.viewer.RequestRepaint;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.VisibilityAndGrouping;
import bdv.viewer.state.SourceGroup;
import bdv.viewer.state.SourceState;
import bdv.viewer.state.ViewerState;
import bdv.viewer.state.XmlIoViewerState;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import mpicbg.spim.data.SpimDataException;
import net.imglib2.Interval;
import net.imglib2.cache.iotiming.CacheIoTiming;
import net.imglib2.realtransform.AffineTransform3D;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;
import net.imglib2.util.Intervals;
import net.imglib2.util.LinAlgHelpers;
import net.imglib2.util.StopWatch;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import tpietzsch.backend.jogl.JoglGpuContext;
import tpietzsch.blocks.ByteUtils;
import tpietzsch.cache.*;
import tpietzsch.dither.DitherBuffer;
import tpietzsch.multires.MultiResolutionStack3D;
import tpietzsch.multires.ResolutionLevel3D;
import tpietzsch.multires.SpimDataStacks;
import tpietzsch.offscreen.OffScreenFrameBuffer;
import tpietzsch.offscreen.OffScreenFrameBufferWithDepth;
import tpietzsch.scene.TexturedUnitCube;
import tpietzsch.shadergen.DefaultShader;
import tpietzsch.shadergen.Shader;
import tpietzsch.shadergen.generate.Segment;
import tpietzsch.shadergen.generate.SegmentTemplate;
import tpietzsch.util.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.File;
import java.io.IOException;
import java.nio.Buffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ForkJoinPool;

import static adaptiveparticles.viewer.AprVolumeRenderer.RepaintType.*;
import static bdv.BigDataViewer.initSetups;
import static bdv.viewer.VisibilityAndGrouping.Event.VISIBILITY_CHANGED;
import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL2ES2.GL_RED;
import static com.jogamp.opengl.GL2ES2.GL_TEXTURE_3D;
import static tpietzsch.backend.Texture.InternalFormat.R16;

public class AprVolumeRenderer implements GLEventListener, RequestRepaint
{
	private final OffScreenFrameBuffer offscreen;

	private final Shader prog;

	private final ArrayList<MultiVolumeShaderMip9> progvols;
	private MultiVolumeShaderMip9 progvol;

	private final WireframeBox box;

	private final DefaultQuad quad;

	private final CacheSpec cacheSpec;
	private final TextureCache textureCache;
	private final PboChain pboChain;
	private final ForkJoinPool forkJoinPool;

	private final ArrayList<VolumeBlocks> volumes;

	private final ArrayList< MultiResolutionStack3D< VolatileUnsignedShortType > > renderStacks = new ArrayList<>();
	private final ArrayList< ConverterSetup > renderConverters = new ArrayList<>();
	private final AffineTransform3D renderTransformWorldToScreen = new AffineTransform3D();

	private final double screenPadding = 0;
	private final double dCam;
	private final double dClip;

	private final CacheControl cacheControl;
	private final Runnable frameRequestRepaint;


	// ... "pre-existing" scene...
	private final TexturedUnitCube[] cubes = new TexturedUnitCube[]{
			new TexturedUnitCube("imglib2.png" ),
			new TexturedUnitCube("fiji.png" ),
			new TexturedUnitCube("imagej2.png" ),
			new TexturedUnitCube("scijava.png" ),
			new TexturedUnitCube("container.jpg" )
	};
	static class CubeAndTransform {
		final TexturedUnitCube cube;
		final Matrix4f model;
		public CubeAndTransform( final TexturedUnitCube cube, final Matrix4f model )
		{
			this.cube = cube;
			this.model = model;
		}
	}
	private final ArrayList< CubeAndTransform > cubeAndTransforms = new ArrayList<>();
	private final OffScreenFrameBufferWithDepth sceneBuf;


	// ... dithering ...
	private final DitherBuffer dither;
	private final int numDitherSteps;


	// ... BDV ...
	private final ViewerState state;
	private final VisibilityAndGrouping visibilityAndGrouping;
	private final SpimDataStacks stacks;
	private final ArrayList< ConverterSetup > converterSetups;
	private final JSlider sliderTime;
	private final ManualTransformation manualTransformation;
	private final SetupAssignments setupAssignments;


	public AprVolumeRenderer(
			final SpimDataMinimal spimData,
			final Runnable frameRequestRepaint,
			final int renderWidth,
			final int renderHeight,
			final int ditherWidth,
			final int ditherStep,
			final int numDitherSamples,
			final int cacheBlockSize,
			final int maxCacheSizeInMB,
			final double dCam,
			final double dClip
	)
	{
		stacks = new SpimDataStacks( spimData );

		final int maxTimepoint = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() - 1;
		final int numVolumes = spimData.getSequenceDescription().getViewSetupsOrdered().size();


		// ---- BDV stuff ----------------------------------------------------
		converterSetups = new ArrayList<>();
		final ArrayList< SourceAndConverter< ? > > sources = new ArrayList<>();
		initSetups( spimData, converterSetups, sources );
		manualTransformation = new ManualTransformation( sources );

		final int numGroups = 10;
		final ArrayList< SourceGroup > groups = new ArrayList<>( numGroups );
		for ( int i = 0; i < numGroups; ++i )
			groups.add( new SourceGroup( "group " + Integer.toString( i + 1 ) ) );
		state = new ViewerState( sources, groups, maxTimepoint + 1 );
		for ( int i = Math.min( numGroups, sources.size() ) - 1; i >= 0; --i )
			state.getSourceGroups().get( i ).addSource( i );

		setupAssignments = new SetupAssignments( converterSetups, 0, 65535 );
		if ( setupAssignments.getMinMaxGroups().size() > 0 )
		{
			final MinMaxGroup group = setupAssignments.getMinMaxGroups().get( 0 );
			for ( final ConverterSetup setup : setupAssignments.getConverterSetups() )
				setupAssignments.moveSetupToGroup( setup, group );
		}

		for ( final ConverterSetup setup : converterSetups )
			setup.setViewer( this );

		visibilityAndGrouping = new VisibilityAndGrouping( state );
		visibilityAndGrouping.addUpdateListener( e -> {
			if ( e.id == VISIBILITY_CHANGED )
				requestRepaint();
		} );

		sliderTime = new JSlider( SwingConstants.HORIZONTAL, 0, maxTimepoint, 0 );
		sliderTime.addChangeListener( e -> {
			if ( e.getSource().equals( sliderTime ) )
				setTimepoint( sliderTime.getValue() );
		} );
		// -------------------------------------------------------------------


		this.cacheControl = stacks.getCacheControl();
		this.frameRequestRepaint = frameRequestRepaint;
		sceneBuf = new OffScreenFrameBufferWithDepth( renderWidth, renderHeight, GL_RGB8 );
		offscreen = new OffScreenFrameBuffer( renderWidth, renderHeight, GL_RGB8 );
		if ( ditherWidth <= 1 )
		{
			dither = null;
			numDitherSteps = 1;
		}
		else
		{
			dither = new DitherBuffer( renderWidth, renderHeight, ditherWidth, ditherStep, numDitherSamples );
			numDitherSteps = dither.numSteps();
		}
		box = new WireframeBox();
		quad = new DefaultQuad();

		this.dCam = dCam;
		this.dClip = dClip;

		cacheSpec = new CacheSpec( R16, new int[] { cacheBlockSize, cacheBlockSize, cacheBlockSize } );
		final int[] cacheGridDimensions = TextureCache.findSuitableGridSize( cacheSpec, maxCacheSizeInMB );
		textureCache = new TextureCache( cacheGridDimensions, cacheSpec );
		pboChain = new PboChain( 5, 100, textureCache );
		final int parallelism = Math.max( 1, Runtime.getRuntime().availableProcessors() / 2 );
		forkJoinPool = new ForkJoinPool( parallelism );

		volumes = new ArrayList<>();
		for ( int i = 0; i < numVolumes; i++ )
			volumes.add( new VolumeBlocks( textureCache ) );

		final Segment ex1vp = new SegmentTemplate("ex1.vp" ).instantiate();
		final Segment ex1fp = new SegmentTemplate("ex1.fp" ).instantiate();
		prog = new DefaultShader( ex1vp.getCode(), ex1fp.getCode() );

		progvols = new ArrayList<>();
		progvols.add( null );
		for ( int i = 1; i <= numVolumes; ++i )
		{
			final MultiVolumeShaderMip9 progvol = new MultiVolumeShaderMip9( i, true, 1.0 );
			progvol.setTextureCache( textureCache );
			progvols.add( progvol );
		}
	}

	@Override
	public void init( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		gl.glPixelStorei( GL_UNPACK_ALIGNMENT, 2 );
		gl.glEnable( GL_DEPTH_TEST );
		gl.glEnable( GL_BLEND );
		gl.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );
	}

	@Override
	public void dispose( final GLAutoDrawable drawable )
	{}

	private double screenWidth = 640;
	private double screenHeight = 480;

	enum RepaintType
	{
		FULL,
		LOAD,
		DITHER
	}

	private class Repaint
	{
		private RepaintType next = FULL;

		synchronized void requestRepaint( RepaintType type )
		{
			switch ( type )
			{
			case FULL:
				next = FULL;
				break;
			case LOAD:
				if ( next != FULL )
					next = LOAD;
				break;
			case DITHER:
				break;
			}
			frameRequestRepaint.run();
		}

		synchronized RepaintType nextRepaint()
		{
			final RepaintType type = next;
			next = DITHER;
			return type;
		}
	}

	private final Repaint repaint = new Repaint();

	private int ditherStep = 0;

	private int targetDitherSteps = 0;

	@Override
	public void display( final GLAutoDrawable drawable )
	{
		final GL3 gl = drawable.getGL().getGL3();
		final JoglGpuContext context = JoglGpuContext.get( gl );

		final RepaintType type = repaint.nextRepaint();

		if ( type == FULL )
		{
			setRenderState();

			ditherStep = 0;
			targetDitherSteps = numDitherSteps;
		}
		else if ( type == LOAD )
		{
			targetDitherSteps = ditherStep + numDitherSteps;
		}

		if ( ditherStep != targetDitherSteps )
		{
			if ( type == FULL || type == LOAD )
			{
				gl.glClearColor( 0.2f, 0.3f, 0.3f, 1.0f );
				gl.glClear( GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT );

				final Matrix4f view = MatrixMath.affine( renderTransformWorldToScreen, new Matrix4f() );
				final Matrix4f projection = MatrixMath.screenPerspective( dCam, dClip, screenWidth, screenHeight, screenPadding, new Matrix4f() );
				final Matrix4f pv = new Matrix4f( projection ).mul( view );

				if ( type == FULL )
				{
					sceneBuf.bind( gl );
					gl.glEnable( GL_DEPTH_TEST );
					gl.glDepthFunc( GL_LESS );
					synchronized ( state )
					{
						for ( CubeAndTransform cubeAndTransform : cubeAndTransforms )
						{
							cubeAndTransform.cube.draw( gl, new Matrix4f( pv ).mul( cubeAndTransform.model ) );
						}
					}
//					// draw volume boxes
//					prog.use( context );
//					prog.getUniformMatrix4f( "view" ).set( view );
//					prog.getUniformMatrix4f( "projection" ).set( projection );
//					prog.getUniform4f( "color" ).set( 1.0f, 0.5f, 0.2f, 1.0f );
//					for ( int i = 0; i < renderStacks.size(); i++ )
//					{
//						final MultiResolutionStack3D< VolatileUnsignedShortType > stack = renderStacks.get( i );
//						prog.getUniformMatrix4f( "model" ).set( MatrixMath.affine( stack.getSourceTransform(), new Matrix4f() ) );
//						prog.setUniforms( context );
//						box.updateVertices( gl, stack.resolutions().get( 0 ).getImage() );
//						box.draw( gl );
//					}

					sceneBuf.unbind( gl, false );
				}

				updateBlocks( context, pv );

				// TODO: fix hacks (initialize OOB block init)
				context.bindTexture( textureCache );
				final int[] ts = cacheSpec.paddedBlockSize();
				final Buffer oobBuffer = Buffers.newDirectShortBuffer( ( int ) Intervals.numElements( ts ) );
				ByteUtils.setShorts( ( short ) 0, ByteUtils.addressOf( oobBuffer ), ( int ) Intervals.numElements( ts ) );
				gl.glTexSubImage3D( GL_TEXTURE_3D, 0, 0, 0, 0, ts[ 0 ], ts[ 1 ], ts[ 2 ], GL_RED, GL_UNSIGNED_SHORT, oobBuffer );

				double minWorldVoxelSize = Double.POSITIVE_INFINITY;
				progvol = progvols.get( renderStacks.size() );
				if ( progvol != null )
				{
					for ( int i = 0; i < renderStacks.size(); i++ )
					{
						progvol.setConverter( i, renderConverters.get( i ) );
						progvol.setVolume( i, volumes.get( i ) );
						minWorldVoxelSize = Math.min( minWorldVoxelSize, volumes.get( i ).getBaseLevelVoxelSizeInWorldCoordinates() );
					}
					progvol.setDepthTexture( sceneBuf.getDepthTexture() );
					progvol.setViewportWidth( offscreen.getWidth() );
					progvol.setProjectionViewMatrix( pv, minWorldVoxelSize );
				}
			}

			if ( dither != null && progvol != null )
			{
				dither.bind( gl );
				progvol.use( context );
				progvol.bindSamplers( context );
				gl.glDepthFunc( GL_ALWAYS );
				gl.glDisable( GL_BLEND );
				final StopWatch stopWatch = StopWatch.createStopped();
				stopWatch.start();
//				final int start = ditherStep;
				while ( ditherStep < targetDitherSteps )
				{
					progvol.setDither( dither, ditherStep % numDitherSteps );
					progvol.setUniforms( context );
					quad.draw( gl );
					gl.glFinish();
					++ditherStep;
					if ( stopWatch.nanoTime() > maxRenderNanos )
						break;
				}
//				final int steps = ditherStep - start;
//				stepList.add( steps );
//				if ( stepList.size() == 1000 )
//				{
//					for ( int step : stepList )
//						System.out.println( "step = " + step );
//					System.out.println();
//					stepList.clear();
//				}
				dither.unbind( gl );
			}
			else
			{
				offscreen.bind( gl, false );
				gl.glDisable( GL_DEPTH_TEST );
				sceneBuf.drawQuad( gl );
				if ( progvol != null )
				{
					gl.glEnable( GL_DEPTH_TEST );
					gl.glDepthFunc( GL_ALWAYS );
					gl.glEnable( GL_BLEND );
					progvol.use( context );
					progvol.bindSamplers( context );
					progvol.setEffectiveViewportSize( offscreen.getWidth(), offscreen.getHeight() );
					progvol.setUniforms( context );
					quad.draw( gl );
				}
				offscreen.unbind( gl, false );
				offscreen.drawQuad( gl );
			}
		}

		if ( dither != null )
		{
			offscreen.bind( gl, false );
			gl.glDisable( GL_DEPTH_TEST );
			sceneBuf.drawQuad( gl );
			if ( progvol != null )
			{
				gl.glEnable( GL_BLEND );
				final int stepsCompleted = Math.min( ditherStep, numDitherSteps );
				dither.dither( gl, stepsCompleted, offscreen.getWidth(), offscreen.getHeight() );
			}
			offscreen.unbind( gl, false );
			offscreen.drawQuad( gl );

			if ( ditherStep != targetDitherSteps )
				repaint.requestRepaint( DITHER );
		}
	}

//	private final ArrayList< Integer > stepList = new ArrayList<>();

	@Override
	public void requestRepaint()
	{
		repaint.requestRepaint( FULL );
	}

	private final long[] iobudget = new long[] { 100L * 1000000L, 10L * 1000000L };

	private final long maxRenderNanos = 30L * 1000000L;

	static class VolumeAndTasks
	{
		private final List< FillTask > tasks;
		private final VolumeBlocks volume;
		private final int maxLevel;

		int numTasks()
		{
			return tasks.size();
		}

		VolumeAndTasks(final List< FillTask > tasks, final VolumeBlocks volume, final int maxLevel )
		{
			this.tasks = new ArrayList<>( tasks );
			this.volume = volume;
			this.maxLevel = maxLevel;
		}
	}

	private void updateBlocks( final JoglGpuContext context, final Matrix4f pv )
	{
		CacheIoTiming.getIoTimeBudget().reset( iobudget );
		cacheControl.prepareNextFrame();

		final int vw = offscreen.getWidth();
//		final int vw = viewportWidth;

		final List< VolumeAndTasks > tasksPerVolume = new ArrayList<>();
		int numTasks = 0;
		for ( int i = 0; i < renderStacks.size(); i++ )
		{
			final MultiResolutionStack3D< VolatileUnsignedShortType > stack = renderStacks.get( i );
			final VolumeBlocks volume = volumes.get( i );
			volume.init( stack, vw, pv );
			final List< FillTask > tasks = volume.getFillTasks();
			numTasks += tasks.size();
			tasksPerVolume.add( new VolumeAndTasks( tasks, volume, stack.resolutions().size() - 1 ) );
		}

A:		while ( numTasks > textureCache.getMaxNumTiles() )
		{
//			System.out.println( "numTasks = " + numTasks );
			tasksPerVolume.sort( Comparator.comparingInt( VolumeAndTasks::numTasks ).reversed() );
			for ( final VolumeAndTasks vat : tasksPerVolume )
			{
				final int baseLevel = vat.volume.getBaseLevel();
				if ( baseLevel < vat.maxLevel )
				{
					vat.volume.setBaseLevel( baseLevel + 1 );
					numTasks -= vat.numTasks();
					vat.tasks.clear();
					vat.tasks.addAll( vat.volume.getFillTasks() );
					numTasks += vat.numTasks();
					continue A;
				}
			}
			break;
		}
//		System.out.println( "final numTasks = " + numTasks + "\n\n" );

		final ArrayList< FillTask > fillTasks = new ArrayList<>();
		for ( final VolumeAndTasks vat : tasksPerVolume )
			fillTasks.addAll( vat.tasks );
		if ( fillTasks.size() > textureCache.getMaxNumTiles() )
			fillTasks.subList( textureCache.getMaxNumTiles(), fillTasks.size() ).clear();

		try
		{
			ProcessFillTasks.parallel( textureCache, pboChain, context, forkJoinPool, fillTasks );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}

		boolean needsRepaint = false;
		for ( int i = 0; i < renderStacks.size(); i++ )
		{
			final VolumeBlocks volume = volumes.get( i );
			final boolean complete = volume.makeLut();
			if ( !complete )
				needsRepaint = true;
			volume.getLookupTexture().upload( context );
		}

		if ( needsRepaint )
			repaint.requestRepaint( LOAD );
	}

	@Override
	public void reshape( final GLAutoDrawable drawable, final int x, final int y, final int width, final int height )
	{
	}

	@SuppressWarnings( "unchecked" )
	private void setRenderState()
	{
		final List< Integer > visibleSourceIndices;
		final int currentTimepoint;
		synchronized ( state )
		{
			visibleSourceIndices = state.getVisibleSourceIndices();
			currentTimepoint = state.getCurrentTimepoint();
			state.getViewerTransform( renderTransformWorldToScreen );

			renderStacks.clear();
			renderConverters.clear();
			for( int i : visibleSourceIndices )
			{
				final MultiResolutionStack3D< VolatileUnsignedShortType > stack = ( MultiResolutionStack3D< VolatileUnsignedShortType > )
						stacks.getStack(
								stacks.timepointId( currentTimepoint ),
								stacks.setupId( i ),
								true );
				final AffineTransform3D sourceTransform = new AffineTransform3D();
				state.getSources().get( i ).getSpimSource().getSourceTransform( currentTimepoint, 0, sourceTransform );
				final MultiResolutionStack3D< VolatileUnsignedShortType > wrappedStack = new MultiResolutionStack3D< VolatileUnsignedShortType >()
				{
					@Override
					public VolatileUnsignedShortType getType()
					{
						return stack.getType();
					}

					@Override
					public AffineTransform3D getSourceTransform()
					{
						return sourceTransform;
					}

					@Override
					public List< ? extends ResolutionLevel3D< VolatileUnsignedShortType > > resolutions()
					{
						return stack.resolutions();
					}
				};
				renderStacks.add( wrappedStack );
				final ConverterSetup converter = converterSetups.get( i );
				renderConverters.add( converter );
			}
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// BDV ViewerPanel equivalents

	/**
	 * Set the viewer transform.
	 */
	public synchronized void setCurrentViewerTransform( final AffineTransform3D viewerTransform )
	{
		state.setViewerTransform( viewerTransform );
		requestRepaint();
	}

	/**
	 * Show the specified time-point.
	 *
	 * @param timepoint
	 *            time-point index.
	 */
	public synchronized void setTimepoint( final int timepoint )
	{
		if ( state.getCurrentTimepoint() != timepoint )
		{
			state.setCurrentTimepoint( timepoint );
			sliderTime.setValue( timepoint );
//			for ( final TimePointListener l : timePointListeners )
//				l.timePointChanged( timepoint );
			requestRepaint();
		}
	}

	/**
	 * Show the next time-point.
	 */
	public synchronized void nextTimePoint()
	{
		if ( state.getNumTimepoints() > 1 )
			sliderTime.setValue( sliderTime.getValue() + 1 );
	}

	/**
	 * Show the previous time-point.
	 */
	public synchronized void previousTimePoint()
	{
		if ( state.getNumTimepoints() > 1 )
			sliderTime.setValue( sliderTime.getValue() - 1 );
	}

	public VisibilityAndGrouping getVisibilityAndGrouping()
	{
		return visibilityAndGrouping;
	}

	public void loadSettings( final String xmlFilename ) throws IOException, JDOMException
	{
		final SAXBuilder sax = new SAXBuilder();
		final Document doc = sax.build( xmlFilename );
		final Element root = doc.getRootElement();
		final XmlIoViewerState io = new XmlIoViewerState();
		io.restoreFromXml( root.getChild( io.getTagName() ), state );
		setupAssignments.restoreFromXml( root );
		manualTransformation.restoreFromXml( root );
		System.out.println( "AprVolumeRenderer.loadSettings" );
	}

	public boolean tryLoadSettings( final String xmlFilename )
	{
		if ( xmlFilename.endsWith( ".xml" ) )
		{
			final String settings = xmlFilename.substring( 0, xmlFilename.length() - ".xml".length() ) + ".settings" + ".xml";
			final File proposedSettingsFile = new File( settings );
			if ( proposedSettingsFile.isFile() )
			{
				try
				{
					loadSettings( settings );
					return true;
				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			}
		}
		return false;
	}

	// -------------------------------------------------------------------------------------------------------

	private Random random = new Random();

	void removeRandomCube()
	{
		synchronized ( state )
		{
			if ( !cubeAndTransforms.isEmpty() )
				cubeAndTransforms.remove( random.nextInt( cubeAndTransforms.size() ) );
		}
		requestRepaint();
	}

	void addRandomCube()
	{
		final AffineTransform3D sourceToWorld = new AffineTransform3D();
		final Interval interval;
		synchronized ( state )
		{
			final int t = state.getCurrentTimepoint();
			final SourceState< ? > source = state.getSources().get( state.getCurrentSource() );
			source.getSpimSource().getSourceTransform( t, 0, sourceToWorld );
			interval = source.getSpimSource().getSource( t, 0 );
		}

		final double[] zero = new double[ 3 ];
		final double[] tzero = new double[ 3 ];
		for ( int d = 0; d < 3; ++d )
			zero[ d ] = interval.min( d );
		sourceToWorld.apply( zero, tzero );

		final double[] one = new double[ 3 ];
		final double[] tone = new double[ 3 ];
		final double[] size = new double[ 3 ];
		for ( int i = 0; i < 3; ++i )
		{
			for ( int d = 0; d < 3; ++d )
				one[ d ] = d == i ? interval.max( d ) + 1 : interval.min( d );
			sourceToWorld.apply( one, tone );
			LinAlgHelpers.subtract( tone, tzero, tone );
			size[ i ] = LinAlgHelpers.length( tone );
		}
		TexturedUnitCube cube = cubes[ random.nextInt( cubes.length ) ];
		Matrix4f model = new Matrix4f()
				.translation(
						( float ) ( tzero[ 0 ] + random.nextDouble() * size[ 0 ] ),
						( float ) ( tzero[ 1 ] + random.nextDouble() * size[ 1 ] ),
						( float ) ( tzero[ 2 ] + random.nextDouble() * size[ 1 ] ) )
				.scale(
						( float ) ( ( random.nextDouble() + 1 ) * size[ 0 ] * 0.05 )	)
				.rotate(
						( float ) ( random.nextDouble() * Math.PI ),
						new Vector3f( random.nextFloat(), random.nextFloat(), random.nextFloat() ).normalize()
				);

		synchronized ( state )
		{
			cubeAndTransforms.add( new CubeAndTransform( cube, model ) );
		}
		requestRepaint();
	}


	public static void run(
			final SpimDataMinimal spimData,
			final int windowWidth,
			final int windowHeight,
			final int renderWidth,
			final int renderHeight,
			final int ditherWidth,
			final int numDitherSamples,
			final int cacheBlockSize,
			final int maxCacheSizeInMB,
			final double dCam,
			final double dClip ) throws SpimDataException
	{
//		final int maxTimepoint = spimData.getSequenceDescription().getTimePoints().getTimePointsOrdered().size() - 1;

		final int ditherStep;
		switch ( ditherWidth )
		{
			case 1:
				ditherStep = 1;
				break;
			case 2:
				ditherStep = 3;
				break;
			case 3:
				ditherStep = 5;
				break;
			case 4:
				ditherStep = 9;
				break;
			case 5:
				ditherStep = 11;
				break;
			case 6:
				ditherStep = 19;
				break;
			case 7:
				ditherStep = 23;
				break;
			case 8:
				ditherStep = 29;
				break;
			default:
				throw new IllegalArgumentException( "unsupported dither width" );
		}

		final InputFrame frame = new InputFrame( "AprVolumeRenderer", windowWidth, windowHeight );
		InputFrame.DEBUG = false;
		final AprVolumeRenderer glPainter = new AprVolumeRenderer(
				spimData,
				frame::requestRepaint,
				renderWidth,
				renderHeight,
				ditherWidth,
				ditherStep,
				numDitherSamples,
				cacheBlockSize,
				maxCacheSizeInMB,
				dCam,
				dClip );
		frame.setGlEventListener( glPainter );
		if ( glPainter.state.getNumTimepoints() > 1 )
		{
			frame.getFrame().getContentPane().add( glPainter.sliderTime, BorderLayout.SOUTH );
			frame.getFrame().pack();
		}

		final TransformHandler tf = frame.setupDefaultTransformHandler( glPainter::setCurrentViewerTransform, () -> {} );

		NavigationActions9.installActionBindings( frame.getKeybindings(), glPainter, new InputTriggerConfig() );
		final BrightnessDialog brightnessDialog = new BrightnessDialog( frame.getFrame(), glPainter.setupAssignments );
		frame.getDefaultActions().namedAction( new ToggleDialogAction( "toggle brightness dialog", brightnessDialog ), "S" );

		final VisibilityAndGroupingDialog activeSourcesDialog = new VisibilityAndGroupingDialog( frame.getFrame(), glPainter.visibilityAndGrouping );
		frame.getDefaultActions().namedAction( new ToggleDialogAction( "toggle active sources dialog", activeSourcesDialog ), "F6" );

		final AffineTransform3D resetTransform = InitializeViewerState.initTransform( windowWidth, windowHeight, false, glPainter.state );
		tf.setTransform( resetTransform );
		frame.getDefaultActions().runnableAction( () -> {
			tf.setTransform( resetTransform );
		}, "reset transform", "R" );

		frame.getDefaultActions().runnableAction( glPainter::addRandomCube, "add random cube", "B" );
		frame.getDefaultActions().runnableAction( glPainter::removeRandomCube, "remove random cube", "shift B" );

		frame.getCanvas().addComponentListener( new ComponentAdapter()
		{
			@Override
			public void componentResized( final ComponentEvent e )
			{
				final int w = frame.getCanvas().getWidth();
				final int h = frame.getCanvas().getHeight();
				tf.setCanvasSize( w, h, true );
				glPainter.screenWidth = w;
				glPainter.screenHeight = h;
				glPainter.requestRepaint();
			}
		} );


//		if ( ! glPainter.tryLoadSettings( xmlFilename ) )
//			InitializeViewerState.initBrightness( 0.001, 0.999, glPainter.state, glPainter.setupAssignments );
		activeSourcesDialog.update();
		glPainter.requestRepaint();
		frame.show();

//		// print fps
//		FPSAnimator animator = new FPSAnimator( frame.getCanvas(), 200 );
//		animator.setUpdateFPSFrames(100, System.out );
//		animator.start();
	}
}
