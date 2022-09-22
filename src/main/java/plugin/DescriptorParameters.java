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

import ij.gui.Roi;

import java.util.ArrayList;

import mpicbg.models.AbstractModel;
import mpicbg.models.InvertibleBoundable;
import mpicbg.models.PointMatch;
import mpicbg.models.TranslationModel2D;
import mpicbg.models.TranslationModel3D;

public class DescriptorParameters 
{	
	/**
	 * How many iterations for a RANSAC
	 */
	public static int ransacIterations = 1000;
	
	/**
	 * minimal number of inliers to number of
	 * candidates in RANSAC
	 */
	public static float minInlierRatio = 0.05f;

	/**
	 * if there is a ROI designed, how many iterations
	 */
	public static int maxIterations = 5;

	/**
	 * Max trust: reject candidates with a cost 
	 * larger than maxTrust * median cost 
	 */
	public static float maxTrust = 4f;
	
	/**
	 * How many times more inliers are required
	 * than the minimum number of correspondences
	 * required for the model.
	 * 
	 * E.g. AffineModel3d needs at least 4 corresponences,
	 * so we reject if the number of inliers is smaller
	 * than minInlierFactor*4 
	 */
	public static float minInlierFactor = 2f;
	
	/**
	 * if true we use filterRANSAC, otherwise only RANSAC
	 */
	public static boolean filterRANSAC = true;
	
	/**
	 * How similar two descriptors at least have to be
	 */
	public static double minSimilarity = 100;
	
	/**
	 * Writes out all corresponding points of all pairs if this is set to a directory
	 */
	public static String correspondenceDirectory = null;

	/**
	 * Just keep the brightest N points of all detections
	 */
	public static int brightestNPoints = 0;

	/**
	 * 0 == compute per image (per timepoint/channel individually)
	 * 1 == compute global min/max
	 * 2 == define min/max
	 */
	public static int minMaxType = 0;
	public static double min = 0;
	public static double max = 0;

	// for debug
	public static boolean printAllSimilarities = false;

	public int dimensionality;
	public double sigma1, sigma2, threshold;
	public int localization = 1; //localizationChoice = { "None", "3-dimensional quadratic fit", "Gaussian mask localization fit" };
	public boolean lookForMaxima, lookForMinima;
	public AbstractModel<?> model;
	public boolean similarOrientation;
	public int numNeighbors;
	public int redundancy;
	public double significance;
	public double ransacThreshold;
	public int channel1, channel2;
	
	public boolean regularize = false;
	public boolean fixFirstTile = true;
	public double lambda = 0.1;
	
	// for stack-registration
	public int globalOpt; // 0=all-to-all; 1=all-to-all-withrange; 2=all-to-1; 3=Consecutive
	public int range;	
	public String directory;
	
	public boolean reApply = false;
	public Roi roi1, roi2;
	
	public boolean setPointsRois = true;
	
	// Display anything?
	public boolean silent = false;

	// 0 == fuse in memory, 1 == write to disk, 2 == nothing
	public int fuse = 0;

	// 0 == linear, 1 == nearest neighbor
	public int interpolation = 0;

	protected AbstractModel< ? > initialModel = null; 
	public AbstractModel<?> getInitialModel()
	{
		if ( initialModel != null )
			return initialModel;
		else if ( this.dimensionality == 2 )
			return new TranslationModel2D();
		else
			return new TranslationModel3D();

	}
	
	// for java-based calling
	public boolean storePoints = false;
	public boolean storeModels = false;
	
	public ArrayList<PointMatch> inliers = null;
	public InvertibleBoundable model1 = null;
	public InvertibleBoundable model2 = null;

	// gaussian parameters
	public double[] sigma;
	public int[] region;
	public int iterations;
}
