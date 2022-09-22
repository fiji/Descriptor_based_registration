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

import mpicbg.models.Point;
import mpicbg.models.PointMatch;

public class ExtendedPointMatch extends PointMatch
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	final double radius1L, radius1W, radius2L, radius2W;
	
	public ExtendedPointMatch( final Point p1, final Point p2, final double radius1L, final double radius1W, final double radius2L, final double radius2W ) 
	{
		super( p1, p2 );
		
		this.radius1L = radius1L;
		this.radius1W = radius1W;
		this.radius2L = radius2L;
		this.radius2W = radius2W;
	}

}
