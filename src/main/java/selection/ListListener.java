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

import java.awt.List;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

public class ListListener implements ItemListener
{
	final List linkedList, thisList;
	final Select_Points parent;
	
	public ListListener( final Select_Points parent, final List thisList, final List linkedList )
	{
		this.thisList = thisList;
		this.linkedList = linkedList;
		this.parent = parent;
	}
	
	@Override
	public void itemStateChanged( ItemEvent e ) 
	{
		if ( e.getStateChange() == ItemEvent.DESELECTED )
		{
			parent.activeIndex = -1; 
			linkedList.deselect( linkedList.getSelectedIndex() );
		}
		else
		{
			parent.activeIndex = thisList.getSelectedIndex(); 
			linkedList.select( thisList.getSelectedIndex() );		
		}
		
		parent.drawCurrentSelection();
	}

}
