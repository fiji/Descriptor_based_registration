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
