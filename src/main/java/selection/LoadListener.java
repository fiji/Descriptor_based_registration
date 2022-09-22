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
package selection;

import ij.IJ;

import java.awt.Frame;
import java.awt.List;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;

import javax.swing.JFileChooser;

import mpicbg.models.Point;

public class LoadListener implements ActionListener
{
	final ArrayList< ExtendedPointMatch > matches;
	final Frame frame;
	final List list1, list2;
	
	public LoadListener( final Frame frame, final List list1, final List list2, final ArrayList< ExtendedPointMatch > matches )
	{
		this.matches = matches;
		this.frame = frame;
		this.list1 = list1;
		this.list2 = list2;
		
	}
	
	@Override
	public void actionPerformed( final ActionEvent arg0 ) 
	{
		String filename = File.separator + "txt";
		JFileChooser fc = new JFileChooser( new File( filename ) );

		// Show open dialog; this method does not return until the dialog is closed
		fc.showOpenDialog(frame);
		File file = fc.getSelectedFile();
		
		IJ.log( "Loading '" + file + "' ..." );
		
		final BufferedReader in = openFileRead( file );
		
		try 
		{
			while ( in.ready() )
			{
				final String[] l = in.readLine().trim().split( "\t" );
				
				if ( l.length != 16 )
				{
					IJ.log( "Wrong line size, cancel." );
					in.close();
					return;
				}
				
				final Point p1 = new Point( new double[] { Double.parseDouble( l[ 0 ] ), Double.parseDouble( l[ 1 ] ), Double.parseDouble( l[ 2 ] ) }, 
											new double[] { Double.parseDouble( l[ 3 ] ), Double.parseDouble( l[ 4 ] ), Double.parseDouble( l[ 5 ] ) } );
				
				final Point p2 = new Point( new double[] { Double.parseDouble( l[ 8 ] ), Double.parseDouble( l[ 9 ] ), Double.parseDouble( l[ 10 ] ) }, 
											new double[] { Double.parseDouble( l[ 11 ] ), Double.parseDouble( l[ 12 ] ), Double.parseDouble( l[ 13 ] ) } );

				final ExtendedPointMatch pm = new ExtendedPointMatch( p1, p2, Double.parseDouble( l[ 6 ] ), Double.parseDouble( l[ 7 ] ), Double.parseDouble( l[ 14 ] ), Double.parseDouble( l[ 15 ] ) );
				
				matches.add( pm );
				list1.add( Select_Points.getStringForPoint( pm.getP1(), pm.radius1W ) );
				list2.add( Select_Points.getStringForPoint( pm.getP2(), pm.radius2W ) );
			}
			
			in.close();
		} 
		catch ( Exception e ) 
		{
			IJ.log( "Cannot load file '" + file + "': " + e );
			e.printStackTrace();
			return;
		}
	}
	
	// sorry ... but I do not want it to depend on SPIM project for that
	private static BufferedReader openFileRead(final File file)
	{
		BufferedReader inputFile;
		try
		{
			inputFile = new BufferedReader(new FileReader(file));
		}
		catch (IOException e)
		{
			System.out.println("TextFileAccess.openFileRead(): " + e);
			inputFile = null;
		}
		return (inputFile);
	}

}
