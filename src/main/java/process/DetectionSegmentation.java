package process;

import ij.IJ;

import java.util.ArrayList;
import java.util.Date;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianReal1;
import mpicbg.imglib.algorithm.scalespace.SubpixelLocalization;
import mpicbg.imglib.image.Image;
import mpicbg.imglib.outofbounds.OutOfBoundsStrategyFactory;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.imglib.wrapper.ImgLib1;
import mpicbg.spim.io.IOFunctions;
import mpicbg.spim.registration.ViewStructure;
import net.imglib2.RandomAccessible;
import net.imglib2.view.Views;
import spim.Threads;

public class DetectionSegmentation
{
	public static double distanceThreshold = 1.5;

	public static ArrayList< DifferenceOfGaussianPeak< FloatType > > extractBeadsLaPlaceImgLib( 
			final Image< FloatType > img,
			final OutOfBoundsStrategyFactory< FloatType > oobsFactory,
			final float imageSigma, 
			final float sigma1,
			final float sigma2,
			float minPeakValue,
			float minInitialPeakValue,
			final boolean findMax,
			final boolean findMin,
			final int localization,
			final int iterations,
			final double[] sigma,
			final int[] region,
			final int debugLevel )
	{
		// we ignore the intensity after the gauss fit for now ...
		if ( localization == 0 || localization == 2 )
			minInitialPeakValue = minPeakValue;

		//
		// Compute the Sigmas for the gaussian folding
		//
		final float[] sigmaXY = new float[]{ sigma1, sigma2 };
		final float[] sigmaDiffXY = computeSigmaDiff( sigmaXY, imageSigma );
		
		final float k = sigmaXY[ 1 ] / sigmaXY[ 0 ];
		final float K_MIN1_INV = computeKWeight(k);
		
		final double[][] sigmaDiff = new double[ 2 ][ 3 ];
		sigmaDiff[ 0 ][ 0 ] = sigmaDiffXY[ 0 ];
		sigmaDiff[ 0 ][ 1 ] = sigmaDiffXY[ 0 ];
		sigmaDiff[ 1 ][ 0 ] = sigmaDiffXY[ 1 ];
		sigmaDiff[ 1 ][ 1 ] = sigmaDiffXY[ 1 ];
		
		// sigmaZ is at least twice the image sigma
		if ( img.getNumDimensions() == 3 )
		{
			final float sigma1Z = Math.max( imageSigma * 2, sigma1 / img.getCalibration( 2 ) );
			final float sigma2Z = sigma1Z * k;
			final float[] sigmaZ = new float[]{ sigma1Z, sigma2Z };
			final float[] sigmaDiffZ = computeSigmaDiff( sigmaZ, imageSigma );
			sigmaDiff[ 0 ][ 2 ] = sigmaDiffZ[ 0 ];
			sigmaDiff[ 1 ][ 2 ] = sigmaDiffZ[ 1 ];
		}

		// compute difference of gaussian
		final DifferenceOfGaussianReal1< FloatType > dog = new DifferenceOfGaussianReal1< FloatType >( img, oobsFactory, sigmaDiff[0], sigmaDiff[1], minInitialPeakValue, K_MIN1_INV );
		dog.setKeepDoGImage( true );
		dog.setNumThreads( Threads.numThreads() );
		
		if ( !dog.checkInput() || !dog.process() )
		{
			if ( debugLevel <= ViewStructure.DEBUG_ERRORONLY )
				IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Cannot compute difference of gaussian for " + dog.getErrorMessage() );
			
			return new ArrayList< DifferenceOfGaussianPeak< FloatType > >();
		}

		// remove all minima
		final ArrayList< DifferenceOfGaussianPeak< FloatType > > peakList = dog.getPeaks();
		for ( int i = peakList.size() - 1; i >= 0; --i )
		{
			if ( !findMin )
			{
				if ( peakList.get( i ).isMin() )
					peakList.remove( i );
			}
			
			if ( !findMax )
			{
				if ( peakList.get( i ).isMax() )
					peakList.remove( i );
			}
		}

		if ( localization == 1 )
		{
			final SubpixelLocalization< FloatType > spl = new SubpixelLocalization< FloatType >( dog.getDoGImage(), dog.getPeaks() );
			spl.setAllowMaximaTolerance( true );
			spl.setMaxNumMoves( 10 );
			spl.setNumThreads( Threads.numThreads() );
			
			if ( !spl.checkInput() || !spl.process() )
			{
				if ( debugLevel <= ViewStructure.DEBUG_ERRORONLY )
					IOFunctions.println("(" + new Date(System.currentTimeMillis()) + "): Warning! Failed to compute subpixel localization " + spl.getErrorMessage() );
			}
			
			//dog.getDoGImage().getDisplay().setMinMax();
			//ImageJFunctions.copyToImagePlus( dog.getDoGImage() ).show();
			dog.getDoGImage().close();
				
			int peakTooLow = 0;
			int invalid = 0;
			int extrema = 0;
			
			// remove entries that are too low
			for ( int i = peakList.size() - 1; i >= 0; --i )
			{
				final DifferenceOfGaussianPeak< FloatType > maximum = peakList.get( i );
				
				if ( !maximum.isValid() )
					++invalid;
				
				if ( findMax )
				{
					if ( maximum.isMax() ) 
					{
						++extrema;
						if ( Math.abs( maximum.getValue().getRealDouble() ) < minPeakValue )
						{
							peakList.remove( i );
							++peakTooLow;
						}
					}
				}
				if ( findMin )
				{
					if ( maximum.isMin() ) 
					{
						++extrema;
						if ( Math.abs( maximum.getValue().getRealDouble() ) < minPeakValue )
						{
							peakList.remove( i );
							++peakTooLow;
						}
					}
				}
			}
			if ( debugLevel <= ViewStructure.DEBUG_ALL )
			{
				IOFunctions.println( "number of peaks: " + dog.getPeaks().size() );
				IOFunctions.println( "invalid: " + invalid );
				IOFunctions.println( "extrema: " + extrema );
				IOFunctions.println( "peak to low: " + peakTooLow );
			}
		}
		else if ( localization == 2 )
		{
			final int n = img.getNumDimensions();

			final long[] min = new long[ n ];
			final long[] max = new long[ n ];
			final int[] p = new int[ n ];
			final double[] loc = new double[ n ];

			int countRemoveDistance = 0;
			int countRemoveBorder = 0;

			final RandomAccessible< net.imglib2.type.numeric.real.FloatType > imgLib2 = ImgLib1.wrapFloatToImgLib2( img );
			
			// gaussian fit (removes and adds the background after its done)
			for ( int i = peakList.size() - 1; i >= 0; --i )
			{
				final DifferenceOfGaussianPeak< FloatType > maximum = peakList.get( i );

				for ( int d = 0; d < n; ++d )
					loc[ d ] = p[ d ] = maximum.getPosition( d );

				if ( !getRangeForFit( min, max, region, p, img ) )
				{
					++countRemoveBorder;
					peakList.remove( i );
					continue;
				}

				GaussianMaskFit.gaussianMaskFit( Views.interval( imgLib2, min, max ), loc, sigma, iterations );

				double distance = 0;
				for ( int d = 0; d < n; ++d )
					distance += (loc[ d ] - p[ d ]) * (loc[ d ] - p[ d ]);
				distance = Math.sqrt( distance );

				if ( distance > distanceThreshold )
				{
					++countRemoveDistance;
					peakList.remove( i );
				}
				else
				{
					for ( int d = 0; d < n; ++d )
						maximum.setSubPixelLocationOffset( (float)loc[ d ] - p[ d ], d );
				}
			}

			IJ.log( "Removed " + countRemoveBorder + " detections because the region was too close to the image boundary (try reducing the support region to reduce this number).");
			IJ.log( "Removed " + countRemoveDistance + " detections because the Gaussian fit moved it by more than " + distanceThreshold + " pixels.");
		}
		
		return peakList;
		
	}

	public static boolean getRangeForFit( final long[] min, final long[] max, final int[] range, final int[] p, final Image<?> img )
	{
		for ( int d = 0; d < p.length; ++d )
		{
			min[ d ] = p[ d ] - range[ d ]/2;
			max[ d ] = p[ d ] + range[ d ]/2;

			if ( min[ d ] < 0 || max[ d ] >= img.getDimension( d ) )
				return false;
		}

		return true;
	}

	public static double computeK( final float stepsPerOctave ) { return Math.pow( 2f, 1f / stepsPerOctave ); }
	public static double computeK( final int stepsPerOctave ) { return Math.pow( 2f, 1f / stepsPerOctave ); }
	public static float computeKWeight( final float k ) { return 1.0f / (k - 1.0f); }
	public static float[] computeSigma( final float k, final float initialSigma )
	{
		final float[] sigma = new float[ 2 ];

		sigma[ 0 ] = initialSigma;
		sigma[ 1 ] = sigma[ 0 ] * k;

		return sigma;
	}
	public static float getDiffSigma( final float sigmaA, final float sigmaB ) { return (float) Math.sqrt( sigmaB * sigmaB - sigmaA * sigmaA ); }
	public static float[] computeSigmaDiff( final float[] sigma, final float imageSigma )
	{
		final float[] sigmaDiff = new float[ 2 ];

		sigmaDiff[ 0 ] = getDiffSigma( imageSigma, sigma[ 0 ] );
		sigmaDiff[ 1 ] = getDiffSigma( imageSigma, sigma[ 1 ] );

		return sigmaDiff;
	}	

}
