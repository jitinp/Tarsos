package be.hogent.tarsos.util.histogram.peaks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import be.hogent.tarsos.util.histogram.Histogram;
import be.hogent.tarsos.util.histogram.ToneScaleHistogram;


public class PeakDetector {


	/**
	 * Create a histogram with peak information. Instead of triangular peaks it creates a histogram (with the
	 * same resolution (bin widths) as the original) with peaks in the form of gaussian curves.
	 * @param histogram the histogram to detect peaks for
	 * @param windowSize the windows to detect peaks in.
	 * @param meanFactorThreshold the threshold when peaks are accepted.
	 * @return a histogram with peak information. Can be used to match with other histograms (files)
	 */
	public static Histogram newPeakDetection(List<Peak> peaks){
		double peakPositionsDouble[] = new double[peaks.size()];
		double peakWidths[] = null;
		double peakHeights[] = new double[peaks.size()];
		double peakStandardDeviations[] = null;
		for(int i=0 ; i < peaks.size();i++){
			peakPositionsDouble[i] = peaks.get(i).getPosition();
			peakHeights[i] = peaks.get(i).getHeight();
		}
		return ToneScaleHistogram.createToneScale(peakPositionsDouble, peakHeights, peakWidths, peakStandardDeviations);
	}

	public static List<Peak> detect(Histogram histogram, int windowSize, double meanFactorThreshold){
		double peakFunctionValues[] = new double[histogram.getNumberOfClasses()];
		PeakScore differenceScore = new DifferenceScore(histogram,windowSize);
		PeakScore localHeightScore = new LocalHeightScore();
		for(int i = 0 ; i < histogram.getNumberOfClasses(); i++){
			double score = differenceScore.score(histogram,i, 1);
			peakFunctionValues[i] = score == 0.0 ? 0.0 : localHeightScore.score(histogram,i, windowSize);
		}

		//double mean = StatUtils.mean(peakFunctionValues);
		//double standardDeviation = Math.pow(StatUtils.variance(peakFunctionValues,mean),0.5);
		List<Integer> peakPositions = new ArrayList<Integer>();
		for(int i = 0;i<histogram.getNumberOfClasses();i++){
			if(peakFunctionValues[i]>meanFactorThreshold){
				peakPositions.add(i);
			}
		}

		Collections.sort(peakPositions);

		List<Integer> elementsToRemove = new ArrayList<Integer>();
		for(int i=0 ; i < peakPositions.size();i++){
			int firstPeakIndex = peakPositions.get(i);
			int secndPeakIndex = peakPositions.get(((i+1) % peakPositions.size()));
			if(Math.abs(secndPeakIndex - firstPeakIndex) <= windowSize)
				elementsToRemove.add(histogram.getCount(firstPeakIndex) > histogram.getCount(secndPeakIndex) ?
						peakPositions.get((i+1) % peakPositions.size()):
						peakPositions.get(i));
		}

		peakPositions.removeAll(elementsToRemove);

		StringBuilder sb = new StringBuilder();
		sb.append(peakPositions.size()).append(";");

		List<Peak> peaks = new ArrayList<Peak>();
		for(int i=0 ; i < peakPositions.size();i++){
			double position = histogram.getKeyForClass(peakPositions.get(i));
			double height = histogram.getCountForClass(peakPositions.get(i));
			peaks.add(new Peak(position, height));
		}
		return peaks;
	}
}