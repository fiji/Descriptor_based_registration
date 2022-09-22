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

import ij.gui.GenericDialog;
import ij.gui.Overlay;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DoneButtonListener implements ActionListener
{
	final Frame frame;
	final Select_Points parent;
	
	public DoneButtonListener( final Select_Points parent, final Frame frame )
	{
		this.frame = frame;
		this.parent = parent;
	}
	
	@Override
	public void actionPerformed( final ActionEvent arg0 ) 
	{ 
		if ( parent.matches.size() > 0 )
		{
			final GenericDialog gd = new GenericDialog( "Query" );
			gd.addMessage( "The list that you created is not empty. Do you really want to quit?" );
			
			gd.showDialog();
			
			if ( gd.wasCanceled() )
				return;
		}
		
		if ( frame != null )
			frame.dispose();
		
		parent.imp1.setOverlay( new Overlay() );
		parent.imp2.setOverlay( new Overlay() );
	}
}
