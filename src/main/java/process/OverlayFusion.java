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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicInteger;

import fiji.stacks.Hyperstack_rearranger;
import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.plugin.Concatenator;
import ij.process.ImageProcessor;
import mpicbg.imglib.container.imageplus.ImagePlusContainer;
import mpicbg.imglib.container.imageplus.ImagePlusContainerFactory;
import mpicbg.imglib.cursor.LocalizableCursor;
import mpicbg.imglib.exception.ImgLibException;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.image.ImageFactory;
import mpicbg.imglib.image.display.imagej.ImageJFunctions;
import mpicbg.imglib.interpolation.Interpolator;
import mpicbg.imglib.interpolation.InterpolatorFactory;
import mpicbg.imglib.interpolation.linear.LinearInterpolatorFactory;
import mpicbg.imglib.interpolation.nearestneighbor.NearestNeighborInterpolatorFactory;
import mpicbg.imglib.multithreading.Chunk;
import mpicbg.imglib.multithreading.SimpleMultiThreading;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyValueFactory;
import mpicbg.imglib.type.numeric.RealType;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.InvertibleCoordinateTransform;
import mpicbg.models.NoninvertibleModelException;
import net.imglib2.util.Util;
import plugin.Descriptor_based_series_registration;

public class OverlayFusion 
{
	public static boolean useSizeOfFirstImage = false;

	protected static <T extends RealType<T>> CompositeImage createOverlay(
			final T targetType,
			final ImagePlus imp1,
			final ImagePlus imp2,
			final InvertibleBoundable finalModel1,
			final InvertibleBoundable finalModel2,
			final int dimensionality,
			final int interpolation )
	{
		final ArrayList< ImagePlus > images = new ArrayList< ImagePlus >();
		images.add( imp1 );
		images.add( imp2 );
		
		final int ntimepoints = Math.min( imp1.getNFrames(), imp2.getNFrames() );
		
		final ImagePlus[] cis = new ImagePlus[ ntimepoints ];
		
		final ArrayList< InvertibleBoundable > models = new ArrayList< InvertibleBoundable >();
		for ( int i = 0; i < ntimepoints; ++i ) 
		{
			models.add( finalModel1 );
			models.add( finalModel2 );
		}

		IJ.log( "Fusing with " + (interpolation == 0 ? "linear interpolation" : "nearest neighbor interpolation" ) );

		for ( int tp = 1; tp <= ntimepoints; ++tp ) 
		{
			if ( interpolation == 1 )
				cis[ tp-1 ] = createOverlay( targetType, images, models, dimensionality, tp, new NearestNeighborInterpolatorFactory<FloatType>( new OutOfBoundsStrategyValueFactory<FloatType>() ) );
			else // 0
				cis[ tp-1 ] = createOverlay( targetType, images, models, dimensionality, tp, new LinearInterpolatorFactory<FloatType>( new OutOfBoundsStrategyValueFactory<FloatType>() ) );
		}

		final ImagePlus imp = new Concatenator().concatenateHyperstacks( cis, "Fused " + imp1.getShortTitle() + " & " + imp2.getShortTitle(), false );

		// get the right number of channels, slices and frames
		final int numChannels = imp1.getNChannels() + imp2.getNChannels();
		final int nSlices = cis[ 0 ].getNSlices();
				
		// Set the right dimensions
		imp.setDimensions( numChannels, nSlices, ntimepoints );
		
		// Copy calibration
		imp.setCalibration( imp1.getCalibration() );
		
		return new CompositeImage( imp, CompositeImage.COMPOSITE );
	}
	
	public static <T extends RealType<T>> ImagePlus createReRegisteredSeries(
			final T targetType,
			final ImagePlus imp,
			final ArrayList<InvertibleBoundable> models,
			final int dimensionality,
			final String directory,
			final int interpolation )
	{
		int numImages;

		if ( Descriptor_based_series_registration.oneModelPerChannel )
			numImages = imp.getNChannels();
		else
			numImages = imp.getNFrames();

		// the size of the new image
		final int[] size = new int[ dimensionality ];
		// the offset relative to the output image which starts with its local coordinates (0,0,0)
		final float[] offset = new float[ dimensionality ];

		final int[][] imgSizes = new int[ numImages ][ dimensionality ];
		
		for ( int i = 0; i < numImages; ++i )
		{
			imgSizes[ i ][ 0 ] = imp.getWidth();
			imgSizes[ i ][ 1 ] = imp.getHeight();
			if ( dimensionality == 3 )
				imgSizes[ i ][ 2 ] = imp.getNSlices();
		}
		
		// estimate the boundaries of the output image and the offset for fusion (negative coordinates after transform have to be shifted to 0,0,0)
		estimateBounds( offset, size, imgSizes, models, dimensionality );

		IJ.log( "Size of output image=" + Util.printCoordinates( size ) );

		// use the same size as the first image, this is a little bit ad-hoc
		if ( useSizeOfFirstImage )
		{
			for ( int d = 0; d < dimensionality; ++d )
			{
				size[ d ] = imgSizes[ 0 ][ d ];
				offset[ d ] = 0;
			}
		}
		
		// for output
		final ImageFactory<T> f = new ImageFactory<T>( targetType, new ImagePlusContainerFactory() );
		// the composite
		final ImageStack stack = new ImageStack( size[ 0 ], size[ 1 ] );

		IJ.log( "Fusing with " + (interpolation == 0 ? "linear interpolation" : "nearest neighbor interpolation" ) );

		for ( int t = 1; t <= imp.getNFrames(); ++t )
		{
			IJ.showProgress( t, imp.getNFrames() );

			for ( int c = 1; c <= imp.getNChannels(); ++c )
			{
				final Image<T> out = f.createImage( size );
				final InvertibleBoundable model;
				
				if ( Descriptor_based_series_registration.oneModelPerChannel )
					model = models.get( c - 1 );
				else
					model = models.get( t - 1 );

				if ( interpolation == 1 )
					fuseChannel( out, ImageJFunctions.convertFloat( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), offset, model, new NearestNeighborInterpolatorFactory<FloatType>( new OutOfBoundsStrategyValueFactory<FloatType>() ) );
				else
					fuseChannel( out, ImageJFunctions.convertFloat( Hyperstack_rearranger.getImageChunk( imp, c, t ) ), offset, model, new LinearInterpolatorFactory<FloatType>( new OutOfBoundsStrategyValueFactory<FloatType>() ) );

				try 
				{
					final ImagePlus outImp = ((ImagePlusContainer<?,?>)out.getContainer()).getImagePlus();

					if ( directory == null )
					{
						// fuse
						for ( int z = 1; z <= out.getDimension( 2 ); ++z )
							stack.addSlice( imp.getTitle(), outImp.getStack().getProcessor( z ) );
					}
					else
					{
						//write to disk
						for ( int z = 1; z <= out.getDimension( 2 ); ++z )
						{
							final ImagePlus tmp = new ImagePlus( "img_t" + lz(t,numImages) + "_z" + lz(z,out.getDimension( 2 ) ) + "_c" + lz( c, imp.getNChannels() ), outImp.getStack().getProcessor( z ) );
							final FileSaver fs = new FileSaver( tmp );
							fs.saveAsTiff( new File( directory, tmp.getTitle() ).getAbsolutePath() );
							tmp.close();
						}
						
						out.close();
						outImp.close();
					}
				} 
				catch (ImgLibException e) 
				{
					IJ.log( "Output image has no ImageJ type: " + e );
				}				
			}
		}

		IJ.showProgress( 1.0 );

		if ( directory != null )
			return null;
		
		//convertXYZCT ...
		ImagePlus result = new ImagePlus( "registered " + imp.getTitle(), stack );
		
		// numchannels, z-slices, timepoints (but right now the order is still XYZCT)
		if ( dimensionality == 3 )
		{
			result.setDimensions( size[ 2 ], imp.getNChannels(), imp.getNFrames() );
			result = OverlayFusion.switchZCinXYCZT( result );
			return new CompositeImage( result, CompositeImage.COMPOSITE );
		}
		else
		{
			//IJ.log( "ch: " + imp.getNChannels() );
			//IJ.log( "slices: " + imp.getNSlices() );
			//IJ.log( "frames: " + imp.getNFrames() );
			result.setDimensions( imp.getNChannels(), 1, imp.getNFrames() );
			
			if ( imp.getNChannels() > 1 )
				return new CompositeImage( result, CompositeImage.COMPOSITE );
			else
				return result;
		}
	}
	
	private static final String lz( final int num, final int max )
	{
		String out = "" + num;
		String outMax = "" + max;
		
		while ( out.length() < outMax.length() )
			out = "0" + out;
		
		return out;
	}
	public static <T extends RealType<T>> CompositeImage createOverlay( final T targetType, final ArrayList<ImagePlus> images, final ArrayList<InvertibleBoundable> models, final int dimensionality, final int timepoint, final InterpolatorFactory< FloatType > factory )
	{	
		final int numImages = images.size();
		
		// the size of the new image
		final int[] size = new int[ dimensionality ];
		// the offset relative to the output image which starts with its local coordinates (0,0,0)
		final float[] offset = new float[ dimensionality ];

		// estimate the boundaries of the output image and the offset for fusion (negative coordinates after transform have to be shifted to 0,0,0)
		estimateBounds( offset, size, images, models, dimensionality );
		
		// for output
		final ImageFactory<T> f = new ImageFactory<T>( targetType, new ImagePlusContainerFactory() );
		// the composite
		final ImageStack stack = new ImageStack( size[ 0 ], size[ 1 ] );
		
		int numChannels = 0;
		
		//loop over all images
		for ( int i = 0; i < images.size(); ++i )
		{
			final ImagePlus imp = images.get( i );
			
			// loop over all channels
			for ( int c = 1; c <= imp.getNChannels(); ++c )
			{
				final Image<T> out = f.createImage( size );
				fuseChannel( out, ImageJFunctions.convertFloat( Hyperstack_rearranger.getImageChunk( imp, c, timepoint ) ), offset, models.get( i + (timepoint - 1) * numImages ), factory );
				try 
				{
					final ImagePlus outImp = ((ImagePlusContainer<?,?>)out.getContainer()).getImagePlus();
					for ( int z = 1; z <= out.getDimension( 2 ); ++z )
						stack.addSlice( imp.getTitle(), outImp.getStack().getProcessor( z ) );
				} 
				catch (ImgLibException e) 
				{
					IJ.log( "Output image has no ImageJ type: " + e );
				}
				
				// count all channels
				++numChannels;
			}
		}

		//convertXYZCT ...
		ImagePlus result = new ImagePlus( "overlay " + images.get( 0 ).getTitle() + " ... " + images.get( numImages - 1 ).getTitle(), stack );
		
		// numchannels, z-slices, timepoints (but right now the order is still XYZCT)
		if ( dimensionality == 3 )
		{
			result.setDimensions( size[ 2 ], numChannels, 1 );
			result = OverlayFusion.switchZCinXYCZT( result );
		}
		else
		{
			result.setDimensions( numChannels, 1, 1 );
		}
		
		return new CompositeImage( result, CompositeImage.COMPOSITE );
	}
	
	/**
	 * Estimate the bounds of the output image. If there are more models than images, we assume that this encodes for more timepoints.
	 * E.g. 2 Images and 10 models would mean 5 timepoints. The arrangement of the models should be as follows:
	 * 
	 * image1 timepoint1
	 * image2 timepoint1
	 * image1 timepoint2
	 * ...
	 * image2 timepoint5
	 * 
	 * @param offset - the offset, will be computed
	 * @param size - the size, will be computed
	 * @param images - all imageplus in a list
	 * @param models - all models
	 * @param dimensionality - which dimensionality (2 or 3)
	 */
	public static void estimateBounds( final float[] offset, final int[] size, final List<ImagePlus> images, final ArrayList<InvertibleBoundable> models, final int dimensionality )
	{
		final int[][] imgSizes = new int[ images.size() ][ dimensionality ];
		
		for ( int i = 0; i < images.size(); ++i )
		{
			imgSizes[ i ][ 0 ] = images.get( i ).getWidth();
			imgSizes[ i ][ 1 ] = images.get( i ).getHeight();
			if ( dimensionality == 3 )
				imgSizes[ i ][ 2 ] = images.get( i ).getNSlices();
		}
		
		estimateBounds( offset, size, imgSizes, models, dimensionality );
	}
	
	/**
	 * Estimate the bounds of the output image. If there are more models than images, we assume that this encodes for more timepoints.
	 * E.g. 2 Images and 10 models would mean 5 timepoints. The arrangement of the models should be as follows:
	 * 
	 * image1 timepoint1
	 * image2 timepoint1
	 * image1 timepoint2
	 * ...
	 * image2 timepoint5
	 * 
	 * @param offset - the offset, will be computed
	 * @param size - the size, will be computed
	 * @param imgSizes - the dimensions of all input images imgSizes[ image ][ x, y, (z) ]
	 * @param models - all models
	 * @param dimensionality - which dimensionality (2 or 3)
	 */
	public static void estimateBounds( final float[] offset, final int[] size, final int[][]imgSizes, final ArrayList<InvertibleBoundable> models, final int dimensionality )
	{
		final int numImages = imgSizes.length;
		final int numTimePoints = models.size() / numImages;
		
		// estimate the bounaries of the output image
		final double[][] max = new double[ numImages * numTimePoints ][];
		final double[][] min = new double[ numImages * numTimePoints ][ dimensionality ];
		
		if ( dimensionality == 2 )
		{
			for ( int i = 0; i < numImages * numTimePoints; ++i )
				max[ i ] = new double[] { imgSizes[ i % numImages ][ 0 ], imgSizes[ i % numImages ][ 1 ] };
		}
		else
		{
			for ( int i = 0; i < numImages * numTimePoints; ++i )
				max[ i ] = new double[] { imgSizes[ i % numImages ][ 0 ], imgSizes[ i % numImages ][ 1 ], imgSizes[ i % numImages ][ 2 ] };
		}
		
		//IJ.log( "1: " + Util.printCoordinates( min[ 0 ] ) + " -> " + Util.printCoordinates( max[ 0 ] ) );
		//IJ.log( "2: " + Util.printCoordinates( min[ 1 ] ) + " -> " + Util.printCoordinates( max[ 1 ] ) );

		// casts of the models
		final ArrayList<InvertibleBoundable> boundables = new ArrayList<InvertibleBoundable>();
		
		for ( int i = 0; i < numImages * numTimePoints; ++i )
		{
			final InvertibleBoundable boundable = (InvertibleBoundable)models.get( i ); 
			boundables.add( boundable );
			
			//IJ.log( "i: " + boundable );
			
			boundable.estimateBounds( min[ i ], max[ i ] );
		}
		//IJ.log( "1: " + Util.printCoordinates( min[ 0 ] ) + " -> " + Util.printCoordinates( max[ 0 ] ) );
		//IJ.log( "2: " + Util.printCoordinates( min[ 1 ] ) + " -> " + Util.printCoordinates( max[ 1 ] ) );
		
		// dimensions of the final image
		final double[] minImg = new double[ dimensionality ];
		final double[] maxImg = new double[ dimensionality ];

		for ( int d = 0; d < dimensionality; ++d )
		{
			// the image might be rotated so that min is actually max
			maxImg[ d ] = Math.max( Math.max( max[ 0 ][ d ], max[ 1 ][ d ] ), Math.max( min[ 0 ][ d ], min[ 1 ][ d ]) );
			minImg[ d ] = Math.min( Math.min( max[ 0 ][ d ], max[ 1 ][ d ] ), Math.min( min[ 0 ][ d ], min[ 1 ][ d ]) );
			
			for ( int i = 2; i < numImages * numTimePoints; ++i )
			{
				maxImg[ d ] = Math.max( maxImg[ d ], Math.max( min[ i ][ d ], max[ i ][ d ]) );
				minImg[ d ] = Math.min( minImg[ d ], Math.min( min[ i ][ d ], max[ i ][ d ]) );	
			}
		}
		
		//IJ.log( "output: " + Util.printCoordinates( minImg ) + " -> " + Util.printCoordinates( maxImg ) );

		// the size of the new image
		//final int[] size = new int[ dimensionality ];
		// the offset relative to the output image which starts with its local coordinates (0,0,0)
		//final float[] offset = new float[ dimensionality ];
		
		for ( int d = 0; d < dimensionality; ++d )
		{
			size[ d ] = (int)Math.round( maxImg[ d ] - minImg[ d ] );
			offset[ d ] = (float)minImg[ d ];
		}
		
		//IJ.log( "size: " + Util.printCoordinates( size ) );
		//IJ.log( "offset: " + Util.printCoordinates( offset ) );		
	}
	
	/**
	 * Fuse one slice/volume (one channel)
	 * 
	 * @param output - same the type of the ImagePlus input
	 * @param input - FloatType, because of Interpolation that needs to be done
	 * @param transform - the transformation
	 */
	public static <T extends RealType<T>> void fuseChannel( final Image<T> output, final Image<FloatType> input, final float[] offset, final InvertibleCoordinateTransform transform, final InterpolatorFactory< FloatType > factory )
	{
		final int dims = output.getNumDimensions();
		long imageSize = output.getDimension( 0 );
		
		for ( int d = 1; d < output.getNumDimensions(); ++d )
			imageSize *= output.getDimension( d );

		// run multithreaded
		final AtomicInteger ai = new AtomicInteger(0);					
        final Thread[] threads = SimpleMultiThreading.newThreads();

        final Vector<Chunk> threadChunks = SimpleMultiThreading.divideIntoChunks( imageSize, threads.length );
        
        for (int ithread = 0; ithread < threads.length; ++ithread)
            threads[ithread] = new Thread(new Runnable()
            {
                public void run()
                {
                	// Thread ID
                	final int myNumber = ai.getAndIncrement();
        
                	// get chunk of pixels to process
                	final Chunk myChunk = threadChunks.get( myNumber );
                	final long startPos = myChunk.getStartPosition();
                	final long loopSize = myChunk.getLoopSize();
                	
            		final LocalizableCursor<T> out = output.createLocalizableCursor();
            		final Interpolator<FloatType> in = input.createInterpolator( factory );
            		
            		final double[] tmp = new double[ input.getNumDimensions() ];
            		
            		try 
            		{
                		// move to the starting position of the current thread
            			out.fwd( startPos );
            			
                		// do as many pixels as wanted by this thread
                        for ( long j = 0; j < loopSize; ++j )
                        {
            				out.fwd();
            				
            				for ( int d = 0; d < dims; ++d )
            					tmp[ d ] = out.getPosition( d ) + offset[ d ];
            				
            				transform.applyInverseInPlace( tmp );
            	
            				in.setPosition( tmp );
            				out.getType().setReal( in.getType().get() );
            			}
            		} 
            		catch (NoninvertibleModelException e) 
            		{
            			IJ.log( "Cannot invert model, qutting." );
            			return;
            		}

                }
            });
        
        SimpleMultiThreading.startAndJoin( threads );
		
        /*
		final LocalizableCursor<T> out = output.createLocalizableCursor();
		final Interpolator<FloatType> in = input.createInterpolator( factory );
		
		final float[] tmp = new float[ input.getNumDimensions() ];
		
		try 
		{
			while ( out.hasNext() )
			{
				out.fwd();
				
				for ( int d = 0; d < dims; ++d )
					tmp[ d ] = out.getPosition( d ) + offset[ d ];
				
				transform.applyInverseInPlace( tmp );
	
				in.setPosition( tmp );			
				out.getType().setReal( in.getType().get() );
			}
		} 
		catch (NoninvertibleModelException e) 
		{
			IJ.log( "Cannot invert model, qutting." );
			return;
		}
		*/
	}

			
	/**
	 * Rearranges an ImageJ XYCZT Hyperstack into XYZCT without wasting memory for processing 3d images as a chunk,
	 * if it is already XYZCT it will shuffle it back to XYCZT
	 * 
	 * @param imp - the input {@link ImagePlus}
	 * @return - an {@link ImagePlus} which can be the same instance if the image is XYZT, XYZ, XYT or XY - otherwise a new instance
	 * containing the same processors but in the new order XYZCT
	 */
	public static ImagePlus switchZCinXYCZT( final ImagePlus imp )
	{
		final int numChannels = imp.getNChannels();
		final int numTimepoints = imp.getNFrames();
		final int numZStacks = imp.getNSlices();

		String newTitle;
		if ( imp.getTitle().startsWith( "[XYZCT]" ) )
			newTitle = imp.getTitle().substring( 8, imp.getTitle().length() );
		else
			newTitle = "[XYZCT] " + imp.getTitle();

		// there is only one channel and one z-stack, i.e. XY(T)
		if ( numChannels == 1 && numZStacks == 1 )
		{
			return imp;
		}
		// there is only one channel XYZ(T) or one z-stack XYC(T)
		else if ( numChannels == 1 || numZStacks == 1 )
		{
			// numchannels, z-slices, timepoints 
			// but of course now reversed...
			imp.setDimensions( numZStacks, numChannels, numTimepoints );
			imp.setTitle( newTitle );
			return imp;
		}
		
		// now we have to rearrange
		final ImageStack stack = new ImageStack( imp.getWidth(), imp.getHeight() );
		
		for ( int t = 1; t <= numTimepoints; ++t )
		{
			for ( int c = 1; c <= numChannels; ++c )
			{
				for ( int z = 1; z <= numZStacks; ++z )
				{
					final int index = imp.getStackIndex( c, z, t );
					final ImageProcessor ip = imp.getStack().getProcessor( index );
					stack.addSlice( imp.getStack().getSliceLabel( index ), ip );
				}
			}
		}
				
		final ImagePlus result = new ImagePlus( newTitle, stack );
		// numchannels, z-slices, timepoints 
		// but of course now reversed...
		result.setDimensions( numZStacks, numChannels, numTimepoints );
		result.getCalibration().pixelWidth = imp.getCalibration().pixelWidth;
		result.getCalibration().pixelHeight = imp.getCalibration().pixelHeight;
		result.getCalibration().pixelDepth = imp.getCalibration().pixelDepth;
		final CompositeImage composite = new CompositeImage( result );
		
		return composite;
	}

	/**
	 * Rearranges an ImageJ XYCTZ Hyperstack into XYCZT without wasting memory for processing 3d images as a chunk,
	 * if it is already XYCTZ it will shuffle it back to XYCZT
	 * 
	 * @param imp - the input {@link ImagePlus}
	 * @return - an {@link ImagePlus} which can be the same instance if the image is XYC, XYZ, XYT or XY - otherwise a new instance
	 * containing the same processors but in the new order XYZCT
	 */
	public static ImagePlus switchZTinXYCZT( final ImagePlus imp )
	{
		final int numChannels = imp.getNChannels();
		final int numTimepoints = imp.getNFrames();
		final int numZStacks = imp.getNSlices();
		
		String newTitle;
		if ( imp.getTitle().startsWith( "[XYCTZ]" ) )
			newTitle = imp.getTitle().substring( 8, imp.getTitle().length() );
		else
			newTitle = "[XYCTZ] " + imp.getTitle();

		// there is only one timepoint and one z-stack, i.e. XY(C)
		if ( numTimepoints == 1 && numZStacks == 1 )
		{
			return imp;
		}
		// there is only one channel XYZ(T) or one z-stack XYC(T)
		else if ( numTimepoints == 1 || numZStacks == 1 )
		{
			// numchannels, z-slices, timepoints 
			// but of course now reversed...
			imp.setDimensions( numChannels, numTimepoints, numZStacks );
			imp.setTitle( newTitle );
			return imp;
		}
		
		// now we have to rearrange
		final ImageStack stack = new ImageStack( imp.getWidth(), imp.getHeight() );
		
		for ( int z = 1; z <= numZStacks; ++z )
		{
			for ( int t = 1; t <= numTimepoints; ++t )
			{
				for ( int c = 1; c <= numChannels; ++c )
				{
					final int index = imp.getStackIndex( c, z, t );
					final ImageProcessor ip = imp.getStack().getProcessor( index );
					stack.addSlice( imp.getStack().getSliceLabel( index ), ip );
				}
			}
		}
		
		final ImagePlus result = new ImagePlus( newTitle, stack );
		// numchannels, z-slices, timepoints 
		// but of course now reversed...
		result.setDimensions( numChannels, numTimepoints, numZStacks );
		result.getCalibration().pixelWidth = imp.getCalibration().pixelWidth;
		result.getCalibration().pixelHeight = imp.getCalibration().pixelHeight;
		result.getCalibration().pixelDepth = imp.getCalibration().pixelDepth;
		final CompositeImage composite = new CompositeImage( result );
		
		return composite;
	}

}
