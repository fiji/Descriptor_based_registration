/*-
 * #%L
 * Fiji distribution of ImageJ for the life sciences.
 * %%
 * Copyright (C) 2011 - 2022 Fiji developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package process;

import net.imglib2.util.Util;
import fiji.util.node.Leaf;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.Point;

public class Particle extends Point implements Leaf<Particle>
{
	private static final long serialVersionUID = 1L;

	final protected int id;
	
	protected DifferenceOfGaussianPeak<FloatType> peak;
	protected double weight = 1;
	protected double distance = -1;
	float diameter = 1;
	float zStretching = 1;

	public Particle( final int id, final DifferenceOfGaussianPeak<FloatType> peak, final float zStretching )
	{
		super( getSubPixelPosition( peak ) );
		this.id = id;
		this.peak = peak;
		this.zStretching = zStretching;
		
		// init
		restoreCoordinates();
	}

	private final static double[] getSubPixelPosition( final DifferenceOfGaussianPeak<FloatType> peak )
	{
		final int n = peak.getNumDimensions();
		final double[] p = new double[ n ];
		for ( int d = 0; d < n; ++d )
			p[ d ] = peak.getSubPixelPosition( d );
		return p;
	}

	public DifferenceOfGaussianPeak<FloatType> getPeak() { return peak; }
	
	/**
	 * Restores the local and global coordinates from the peak that feeded it initially,
	 * they might have been changed by applying a model during the optimization
	 */
	public void restoreCoordinates()
	{
		for ( int d = 0; d < l.length; ++d )
			l[ d ] = w[ d ] = peak.getSubPixelPosition( d );

		// apply the z-stretching if it is 3d
		if ( l.length >= 3 )
		{
			l[ 2 ] *= zStretching;
			w[ 2 ] *= zStretching;
		}
	}
	
	public long getID() { return id; }
	public void setWeight( final double weight ){ this.weight = weight; }
	public double getWeight(){ return weight; }
	public void setDiameter( final float diameter ) { this.diameter = diameter; }
	public float getDiameter() { return diameter; }

	final public void setW( final float[] wn )
	{
		for ( int i = 0; i < Math.min( w.length, wn.length ); ++i )
			w[i] = wn[i];
	}
	
	final public void resetW()
	{
		for ( int i = 0; i < w.length; ++i )
			w[i] = l[i];
	}

	protected boolean useW = true;
	
	public void setUseW( final boolean useW ) { this.useW = useW; } 
	public boolean getUseW() { return useW; } 
	
	public void setDistance( double distance ) { this.distance = distance;	}
	public double getDistance() { return this.distance;	}


	@Override
	public float get( final int k ) 
	{
		if ( useW )
			return (float)w[ k ];
		else
			return (float)l[ k ];
	}	
	
	public void set( final float v, final int k ) 
	{
		if ( useW )
			w[ k ] = v;
		else
			l[ k ] = v;
	}	

	@Override
	public String toString()
	{
		return "DustParticle " + getID() + " l"+ Util.printCoordinates( getL() ) + "; w"+ Util.printCoordinates( getW() );		
	}

	public boolean isLeaf() { return true; }

	@Override
	public float distanceTo( final Particle o )
	{
		double distance = 0;
		
		for ( int d = 0; d < l.length; ++d )
		{
			final double a = o.get( d ) - get( d );
			distance += a*a;
		}
		
		return (float)Math.sqrt( distance );
	}
	
	@Override
	public Particle[] createArray( final int n ){ return new Particle[ n ];	}

	@Override
	public int getNumDimensions(){ return l.length; }
	
	public boolean equals( final Particle o )
	{
		if ( useW )
		{
			for ( int d = 0; d < l.length; ++d )
				if ( w[ d ] != o.w[ d ] )
					return false;			
		}
		else
		{
			for ( int d = 0; d < l.length; ++d )
				if ( l[ d ] != o.l[ d ] )
					return false;						
		}
				
		return true;
	}
	
	public static boolean equals( final Particle nucleus1, final Particle nucleus2 )
	{
		if ( nucleus1.getID() == nucleus2.getID() )
			return true;
		else
			return false;
	}	
}
