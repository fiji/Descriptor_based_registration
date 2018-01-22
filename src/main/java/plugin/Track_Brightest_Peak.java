package plugin;

import fiji.plugin.Bead_Registration;
import fiji.stacks.Hyperstack_rearranger;
import fiji.util.gui.GenericDialogPlus;
import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.MultiLineLabel;
import ij.plugin.PlugIn;

import java.io.File;
import java.util.ArrayList;

import mpicbg.imglib.algorithm.scalespace.DifferenceOfGaussianPeak;
import mpicbg.imglib.type.numeric.real.FloatType;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.Model;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;
import mpicbg.spim.segmentation.InteractiveDoG;
import process.Matching;
import process.OverlayFusion;

public class Track_Brightest_Peak implements PlugIn
{

	@Override
	public void run( final String arg0 ) 
	{
		// get list of image stacks
		final int[] idList = WindowManager.getIDList();		

		if ( idList == null || idList.length < 1 )
		{
			IJ.error( "You need at least one open image." );
			return;
		}
		
		final String[] imgList = new String[ idList.length ];
		for ( int i = 0; i < idList.length; ++i )
			imgList[ i ] = WindowManager.getImage(idList[i]).getTitle();

		if ( Descriptor_based_series_registration.defaultImg >= imgList.length )
			Descriptor_based_series_registration.defaultImg = 0;
		
		/**
		 * The first dialog for choosing the images
		 */
		final GenericDialog gd = new GenericDialog( "Brightest point tracking" );
	
		gd.addChoice("Series_of_images", imgList, imgList[ Descriptor_based_series_registration.defaultImg ] );
		
		if ( Descriptor_based_series_registration.lastModels != null )
			gd.addCheckbox( "Reapply last models", Descriptor_based_series_registration.defaultReApply );
		
		gd.addMessage( "Warning: if images are of RGB or 8-bit color they will be converted to hyperstacks.");
		gd.addMessage( "Please note that the SPIM Registration is based on a publication.\n" +
					   "If you use it successfully for your research please be so kind to cite our work:\n" +
					   "Preibisch et al., Nature Methods (2010), 7(6):418-419\n" );

		MultiLineLabel text =  (MultiLineLabel) gd.getMessage();
		Bead_Registration.addHyperLinkListener( text, Descriptor_based_series_registration.paperURL );

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		ImagePlus imp = WindowManager.getImage( idList[ Descriptor_based_series_registration.defaultImg = gd.getNextChoiceIndex() ] );		
		boolean reApply = false;
		
		if ( Descriptor_based_series_registration.lastModels != null )
		{
			reApply = gd.getNextBoolean();
			Descriptor_based_series_registration.defaultReApply = reApply;			
		}

		// if one of the images is rgb or 8-bit color convert them to hyperstack
		imp = Hyperstack_rearranger.convertToHyperStack( imp );
		
		if ( imp.getNSlices() == 1 && imp.getNFrames() == 1)
		{
			IJ.log( "The image has only one slice/frame, cannot perform stack registration" );
			return;
		}

		final int dimensionality;
		
		// is it 2d over time or 3d over time
		if ( imp.getNSlices() > 1 && imp.getNFrames() > 1 )
			dimensionality = 3;
		else
			dimensionality = 2;
		
		// if it is a stack, we convert it into a movie
		if ( dimensionality == 2 && imp.getNSlices() > 1 )
			imp = OverlayFusion.switchZTinXYCZT( imp );

		// reapply?
		if ( reApply )
		{
			if ( Descriptor_based_series_registration.lastModels.size() < imp.getNFrames() )
			{
				IJ.log( "Cannot reapply, you have only " + Descriptor_based_series_registration.lastModels.size() + " models, but the series size is" + imp.getNFrames() + "." );
				Descriptor_based_series_registration.defaultReApply = false;
				return;
			}
			else if ( Descriptor_based_series_registration.lastModels.size() > imp.getNFrames() )
			{
				IJ.log( "WARNING: you have " + Descriptor_based_series_registration.lastModels.size() + " models, but the series size is only" + imp.getNFrames() + "." );
			}
			else if ( dimensionality < Descriptor_based_series_registration.lastDimensionality )
			{
				IJ.log( "Cannot reapply, cannot apply " + Descriptor_based_series_registration.lastModels.get( 0 ).getClass().getSimpleName() + " to " + dimensionality + " data." );
				Descriptor_based_series_registration.defaultReApply = false;
				return;
			}
			else if ( dimensionality > Descriptor_based_series_registration.lastDimensionality )
			{
				IJ.log( "WARNING: applying " + Descriptor_based_series_registration.lastModels.get( 0 ).getClass().getSimpleName() + " to " + dimensionality + " data." );
			}

			final GenericDialog gd2 = new GenericDialog( "Descriptor based stack registration" );
			gd2.addChoice( "Image fusion", Descriptor_based_series_registration.resultChoices, Descriptor_based_series_registration.resultChoices[ Descriptor_based_series_registration.defaultResult ] );

			gd2.showDialog();
			if ( gd2.wasCanceled() )
				return;

			final int result = Descriptor_based_series_registration.defaultResult = gd2.getNextChoiceIndex();

			if ( Descriptor_based_series_registration.defaultResult == 1 )
			{
				final GenericDialogPlus gd3 = new GenericDialogPlus( "Select output directory" );
				gd3.addDirectoryField( "Output_directory", Descriptor_based_series_registration.defaultDirectory, 60 );
				gd3.showDialog();
				
				if ( gd3.wasCanceled() )
					return;
				
				Descriptor_based_series_registration.defaultDirectory = gd3.getNextString();
			}
			
			// just fuse
			final DescriptorParameters params = new DescriptorParameters();
			params.reApply = true;
			params.dimensionality = dimensionality;
			params.fuse = result;
			params.directory = Descriptor_based_series_registration.defaultDirectory;
			Matching.descriptorBasedStackRegistration( imp, params );
			return;
		}

		// open a second dialog and query the other parameters
		final DescriptorParameters params = getParameters( imp, dimensionality );
		
		if ( params == null )
			return;
		
		// compute the matching
		final int numImages = imp.getNFrames();

		// zStretching if applicable
		final float zStretching = params.dimensionality == 3 ? (float)imp.getCalibration().pixelDepth / (float)imp.getCalibration().pixelWidth : 1;

		float[] minmax;

		if ( DescriptorParameters.minMaxType == 0 )
			minmax = null;
		else if ( DescriptorParameters.minMaxType == 1 )
			minmax = Matching.computeMinMax( imp, params.channel1 );
		else
			minmax = new float[]{ (float)DescriptorParameters.min, (float)DescriptorParameters.max };

		if ( minmax != null )
		{
			IJ.log( "min=" + minmax[ 0 ] );
			IJ.log( "max=" + minmax[ 1 ] );
		}

		// one peak per frame
		Descriptor_based_series_registration.lastModels = new ArrayList<InvertibleBoundable>();

		double[] firstPos = null;
		double[] vector = new double[ params.dimensionality ];

		for ( int t = 0; t < numImages; ++t )
		{
			final DifferenceOfGaussianPeak<FloatType> peak = brightestPeak( Matching.extractCandidates( imp, params.channel1, t, params, minmax ) );
			
			if ( firstPos == null )
			{
				firstPos = new double[ params.dimensionality ];

				for ( int d = 0; d < params.dimensionality; ++d )
					firstPos[ d ] = peak.getSubPixelPosition( d );

				if ( params.dimensionality == 2 )
					Descriptor_based_series_registration.lastModels.add(  new TranslationModel2D() );
				else
					Descriptor_based_series_registration.lastModels.add(  new TranslationModel3D() );
			}
			else
			{
				for ( int d = 0; d < params.dimensionality; ++d )
				{
					vector[ d ] = peak.getSubPixelPosition( d ) - firstPos[ d ];
				}

				if ( params.dimensionality == 2 )
				{
					TranslationModel2D m = new TranslationModel2D();
					m.set( -vector[ 0 ], -vector[ 1 ] );
					Descriptor_based_series_registration.lastModels.add( m );
				}
				else
				{
					TranslationModel3D m = new TranslationModel3D();
					m.set( -vector[ 0 ], -vector[ 1 ], -vector[ 2 ] );
					Descriptor_based_series_registration.lastModels.add( m );
				}
			}
		}

		Descriptor_based_series_registration.lastDimensionality = params.dimensionality;

		// fuse by calling re-apply
		params.reApply = true;
		Matching.descriptorBasedStackRegistration( imp, params );
	}

	public static DifferenceOfGaussianPeak<FloatType> brightestPeak( final ArrayList< DifferenceOfGaussianPeak< FloatType > > peaks )
	{
		DifferenceOfGaussianPeak< FloatType > max = peaks.get(  0 );

		for ( final DifferenceOfGaussianPeak< FloatType > peak : peaks )
			if ( Math.abs( peak.getValue().get() ) > Math.abs( max.getValue().get() ) )
				max = peak;

		return max;
	}

	/**
	 * Ask for all other required parameters ..
	 * 
	 * @param dimensionality
	 */
	protected DescriptorParameters getParameters( final ImagePlus imp, final int dimensionality )
	{
		// one of them is by default interactive, then all are interactive
		if ( Descriptor_based_series_registration.defaultDetectionBrightness == Descriptor_based_series_registration.detectionBrightness.length - 1 || 
				Descriptor_based_series_registration.defaultDetectionSize == Descriptor_based_series_registration.detectionSize.length - 1 ||
				Descriptor_based_series_registration.defaultDetectionType == Descriptor_based_series_registration.detectionTypes.length - 1 )
		{
			Descriptor_based_series_registration.defaultDetectionBrightness = Descriptor_based_series_registration.detectionBrightness.length - 1; 
			Descriptor_based_series_registration.defaultDetectionSize = Descriptor_based_series_registration.detectionSize.length - 1;
			Descriptor_based_series_registration.defaultDetectionType = Descriptor_based_series_registration.detectionTypes.length - 1;
		}
		
		final GenericDialog gd = new GenericDialog( "Descriptor based stack registration" );			
		
		gd.addChoice( "Brightness_of detections", Descriptor_based_series_registration.detectionBrightness, Descriptor_based_series_registration.detectionBrightness[ Descriptor_based_series_registration.defaultDetectionBrightness ] );
		gd.addChoice( "Approximate_size of detections", Descriptor_based_series_registration.detectionSize, Descriptor_based_series_registration.detectionSize[ Descriptor_based_series_registration.defaultDetectionSize ] );
		gd.addChoice( "Type_of_detections", Descriptor_based_series_registration.detectionTypes, Descriptor_based_series_registration.detectionTypes[ Descriptor_based_series_registration.defaultDetectionType ] );
		gd.addChoice( "Subpixel_Localization", Descriptor_based_series_registration.localizationChoice, Descriptor_based_series_registration.localizationChoice[ Descriptor_based_series_registration.defaultLocalization ] );

		final int numChannels = imp.getNChannels();

		gd.addSlider( "Choose_registration_channel" , 1, numChannels, Descriptor_based_series_registration.defaultChannel );
		gd.addMessage( "" );
		gd.addChoice( "Image fusion", Descriptor_based_series_registration.resultChoices, Descriptor_based_series_registration.resultChoices[ Descriptor_based_series_registration.defaultResult ] );
		
		gd.addMessage("");
		gd.addMessage("This Plugin is developed by Stephan Preibisch\n" + Descriptor_based_series_registration.myURL);

		MultiLineLabel text = (MultiLineLabel) gd.getMessage();
		Bead_Registration.addHyperLinkListener(text, Descriptor_based_series_registration.myURL);

		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return null;
		
		final DescriptorParameters params = new DescriptorParameters();
		params.roi1 = imp.getRoi();
		
		final int detectionBrightnessIndex = gd.getNextChoiceIndex();
		final int detectionSizeIndex = gd.getNextChoiceIndex();
		final int detectionTypeIndex = gd.getNextChoiceIndex();
		final int localization = gd.getNextChoiceIndex();

		// zero-offset channel
		final int channel = (int)Math.round( gd.getNextNumber() ) - 1;
		final int result = gd.getNextChoiceIndex();
		
		// update static values for next call
		Descriptor_based_series_registration.defaultDetectionBrightness = detectionBrightnessIndex;
		Descriptor_based_series_registration.defaultDetectionSize = detectionSizeIndex;
		Descriptor_based_series_registration.defaultDetectionType = detectionTypeIndex;
		Descriptor_based_series_registration.defaultLocalization = localization;
		Descriptor_based_series_registration.defaultChannel = channel + 1;
		Descriptor_based_series_registration.defaultResult = result;

		// instantiate model
		if ( dimensionality == 2 )
			params.model = new TranslationModel2D();
		else
			params.model = new TranslationModel3D();

		params.regularize = false;
		params.fixFirstTile = true;

		if ( result == 1 )
		{
			final GenericDialogPlus gd2 = new GenericDialogPlus( "Select output directory" );
			gd2.addDirectoryField( "Output_directory", Descriptor_based_series_registration.defaultDirectory, 60 );
			gd2.showDialog();
			
			if ( gd2.wasCanceled() )
				return null;
			
			Descriptor_based_series_registration.defaultDirectory = gd2.getNextString();
		}
			
		// one of them is by default interactive, then all are interactive
		if ( detectionBrightnessIndex == Descriptor_based_series_registration.detectionBrightness.length - 1 || 
			 detectionSizeIndex == Descriptor_based_series_registration.detectionSize.length - 1 ||
			 detectionTypeIndex == Descriptor_based_series_registration.detectionTypes.length - 1 )
		{
			// query parameters interactively
			final double[] values = new double[]{ Descriptor_based_series_registration.defaultSigma, Descriptor_based_series_registration.defaultThreshold };
			
			final ImagePlus interactiveTmp;
			
			if ( dimensionality == 2 )
			{
				interactiveTmp = new ImagePlus( "First slice of " + imp.getTitle(), imp.getStack().getProcessor( imp.getStackIndex( channel + 1, 1, 1 ) ) );
			}
			else
			{
				ImageStack stack = new ImageStack( imp.getWidth(), imp.getHeight() );
				for ( int s = 1; s <= imp.getNSlices(); ++s )
					stack.addSlice( "", imp.getStack().getProcessor( imp.getStackIndex( channel + 1, s, 1 ) ) );
				interactiveTmp = new ImagePlus( "First series of " + imp.getTitle(), stack );
			}
			interactiveTmp.show();
			final InteractiveDoG idog = Descriptor_based_registration.getInteractiveDoGParameters( interactiveTmp, 1, values, 20 );
			interactiveTmp.close();

			if ( idog.wasCanceled() )
				return null;

			params.sigma1 = values[ 0 ];
			params.threshold = values[ 1 ];
			params.lookForMaxima = idog.getLookForMaxima();
			params.lookForMinima = idog.getLookForMinima();
		}
		else 
		{
			if ( detectionBrightnessIndex == Descriptor_based_series_registration.detectionBrightness.length - 2 || detectionSizeIndex == Descriptor_based_series_registration.detectionSize.length - 2 )
			{
				// ask for the dog parameters
				final double[] values = Descriptor_based_registration.getAdvancedDoGParameters( Descriptor_based_series_registration.defaultSigma, Descriptor_based_series_registration.defaultThreshold );
				params.sigma1 = values[ 0 ];
				params.threshold = values[ 1 ];				
			}
			else
			{
				if ( detectionBrightnessIndex == 0 )
					params.threshold = 0.001;			
				else if ( detectionBrightnessIndex == 1 )
					params.threshold = 0.008;			
				else if ( detectionBrightnessIndex == 2 )
					params.threshold = 0.03;			
				else if ( detectionBrightnessIndex == 3 )
					params.threshold = 0.1;
	
				params.sigma1 = (detectionSizeIndex + 2.0) / 2.0;
			}
			
			if ( detectionTypeIndex == 2 )
			{
				params.lookForMaxima = true;
				params.lookForMinima = true;
			}
			else if ( detectionTypeIndex == 1 )
			{
				params.lookForMinima = true;
				params.lookForMaxima = false;
			}
			else
			{
				params.lookForMinima = false;
				params.lookForMaxima = true;
			}
		}

		if ( localization == 2 && !Descriptor_based_series_registration.getGaussianParameters( dimensionality, params ) )
			return null;

		// set the new default values
		Descriptor_based_series_registration.defaultSigma = params.sigma1;
		Descriptor_based_series_registration.defaultThreshold = params.threshold;
		
		if ( params.lookForMaxima && params.lookForMinima )
			Descriptor_based_series_registration.defaultDetectionType = 2;
		else if ( params.lookForMinima )
			Descriptor_based_series_registration.defaultDetectionType = 1;
		else
			Descriptor_based_series_registration.defaultDetectionType = 0;
	
		// other parameters
		params.sigma2 = InteractiveDoG.computeSigma2( (float)params.sigma1, InteractiveDoG.standardSensitivity );
		params.channel1 = channel; 
		params.channel2 = -1;
		params.fuse = result;
		params.directory = Descriptor_based_series_registration.defaultDirectory;
		params.setPointsRois = false;
		params.dimensionality = dimensionality;
		params.localization = localization;
		
		return params;
	}

	public static void main( String[] args )
	{
		File f = new File( "REP_MOVIE mTmT EB3-GFP short sequence.tif" );
		
		new ImageJ();
		IJ.log( f.getAbsolutePath() + " exists: " + f.exists() );
		new ImagePlus( f.getAbsolutePath() ).show();
		new Track_Brightest_Peak().run( null );
	}
}
