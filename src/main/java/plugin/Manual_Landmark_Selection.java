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
package plugin;

import ij.IJ;
import ij.ImageJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.Opener;
import ij.plugin.PlugIn;
import selection.Select_Points;

public class Manual_Landmark_Selection implements PlugIn
{
	public static int defaultImg1 = 0;
	public static int defaultImg2 = 1;
	
	
	@Override
	public void run(String arg0) 
	{
		// get list of image stacks
		final int[] idList = WindowManager.getIDList();		

		if ( idList == null || idList.length < 2 )
		{
			IJ.error( "You need at least two open images." );
			return;
		}

		final String[] imgList = new String[ idList.length ];
		for ( int i = 0; i < idList.length; ++i )
			imgList[ i ] = WindowManager.getImage(idList[i]).getTitle();

		if ( defaultImg1 >= imgList.length || defaultImg2 >= imgList.length )
		{
			defaultImg1 = 0;
			defaultImg2 = 1;
		}

		/**
		 * The first dialog for choosing the images
		 */
		final GenericDialog gd = new GenericDialog( "Descriptor based registration" );
	
		gd.addChoice("First_image", imgList, imgList[ defaultImg1 ] );
		gd.addChoice("Second_image", imgList, imgList[ defaultImg2 ] );
		
		gd.showDialog();
		
		if ( gd.wasCanceled() )
			return;
		
		final ImagePlus imp1 = WindowManager.getImage( idList[ defaultImg1 = gd.getNextChoiceIndex() ] );		
		final ImagePlus imp2 = WindowManager.getImage( idList[ defaultImg2 = gd.getNextChoiceIndex() ] );		
		
		new Select_Points( imp1, imp2 ).run( null );		
	}

	public static void main( String[] args )
	{
		new ImageJ();
		
		final Opener open = new Opener();
		final ImagePlus imp1 = open.openImage( "/Users/preibischs/Microscopy/Dauer/SPIM/hmg-11-6th.mdb/spim_TL1_Angle20.tif" );
		final ImagePlus imp2 = open.openImage( "/Users/preibischs/Microscopy/Dauer/SPIM/hmg-11-6th.mdb/spim_TL1_Angle110.tif" );
		
		imp1.show();
		imp2.show();
		
		new Select_Points( imp1, imp2 ).run( null );
	}
}
