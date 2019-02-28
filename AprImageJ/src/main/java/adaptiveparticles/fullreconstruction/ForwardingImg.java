package adaptiveparticles.fullreconstruction;

import net.imglib2.*;
import net.imglib2.img.Img;
import net.imglib2.img.ImgFactory;

import java.util.Iterator;

/**
 * Just a wrapper around an {@link Img}.
 * It's very useful when you implement something like {@link AprImg}.
 */
// TODO: this should be part of imglib2 core
public class ForwardingImg<T> implements Img<T>
{
	private Img<T> img;

	public ForwardingImg(Img<T> aImage) {
		img = aImage;
	}

	@Override public ImgFactory<T> factory()
	{
		return img.factory();
	}

	@Override public Img<T> copy()
	{
		return img.copy();
	}

	@Override public Cursor<T> cursor()
	{
		return img.cursor();
	}

	@Override public Cursor<T> localizingCursor()
	{
		return img.localizingCursor();
	}

	@Override public long size()
	{
		return img.size();
	}

	@Override public T firstElement()
	{
		return img.firstElement();
	}

	@Override public Object iterationOrder()
	{
		return img.iterationOrder();
	}

	@Override public Iterator<T> iterator()
	{
		return img.iterator();
	}

	@Override public long min( int i )
	{
		return img.min( i );
	}

	@Override public void min( long[] longs )
	{
		img.min( longs );
	}

	@Override public void min( Positionable positionable )
	{
		img.min( positionable );
	}

	@Override public long max( int i )
	{
		return img.max( i );
	}

	@Override public void max( long[] longs )
	{
		img.max( longs );
	}

	@Override public void max( Positionable positionable )
	{
		img.max( positionable );
	}

	@Override public void dimensions( long[] longs )
	{
		img.dimensions( longs );
	}

	@Override public long dimension( int i )
	{
		return img.dimension( i );
	}

	@Override public RandomAccess<T> randomAccess()
	{
		return img.randomAccess();
	}

	@Override public RandomAccess<T> randomAccess( Interval interval )
	{
		return img.randomAccess( interval );
	}

	@Override public double realMin( int i )
	{
		return img.realMin( i );
	}

	@Override public void realMin( double[] doubles )
	{
		img.realMin( doubles );
	}

	@Override public void realMin( RealPositionable realPositionable )
	{
		img.realMin( realPositionable );
	}

	@Override public double realMax( int i )
	{
		return img.realMax( i );
	}

	@Override public void realMax( double[] doubles )
	{
		img.realMax( doubles );
	}

	@Override public void realMax( RealPositionable realPositionable )
	{
		img.realMax( realPositionable );
	}

	@Override public int numDimensions()
	{
		return img.numDimensions();
	}
}
