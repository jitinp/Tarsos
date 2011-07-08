/**
 */
package be.hogent.tarsos.ui.pitch;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import be.hogent.tarsos.util.AudioFile;
import be.hogent.tarsos.util.histogram.Histogram;
import be.hogent.tarsos.util.histogram.peaks.Peak;
import be.hogent.tarsos.util.histogram.peaks.PeakDetector;

import com.jgoodies.forms.builder.DefaultFormBuilder;
import com.jgoodies.forms.layout.FormLayout;

/**
 * @author Joren Six
 */
public final class HistogramLayer implements Layer, ScaleChangedListener, AudioFileChangedListener {

	private final JComponent parent;
	private final MouseDragListener mouseDrag;
	private final Histogram histo;
	private final int maxMarkers = 50;
	private final List<Double> markerPositions;
	private final ScaleChangedListener scaleChangedPublisher;
	private final Color histogramColor;

	/**
	 * Log messages.
	 */
	private static final Logger LOG = Logger.getLogger(HistogramLayer.class.getName());

	public HistogramLayer(final JComponent component, final Histogram histogram,
			final ScaleChangedListener scalePublisher, final Color color) {
		parent = component;
		mouseDrag = new MouseDragListener(component, MouseEvent.BUTTON1);
		histo = histogram;
		component.addMouseListener(mouseDrag);
		component.addMouseMotionListener(mouseDrag);
		markerPositions = new ArrayList<Double>();
		scaleChangedPublisher = scalePublisher;
		histogramColor = color;
	}

	public void setMarkers(final List<Double> newMarkers) {
		// add new markers with ttl 0
		for (Double newMarker : newMarkers) {
			markerPositions.add(newMarker);
		}

		// remove old markers
		while (markerPositions.size() > maxMarkers) {
			markerPositions.remove(0);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see be.hogent.tarsos.ui.Layer#draw()
	 */
	public void draw(final Graphics2D graphics) {
		double xOffset = mouseDrag.calculateXOffset();
		int yOffset = 20;
		final double delta = histo.getStop() - histo.getStart();
		final long maxCount = histo.getMaxBinCount();

		final int width = parent.getWidth();
		final int height = parent.getHeight();

		final int xOffsetPixels = (int) Math.round(xOffset * width);
		int x = xOffsetPixels;

		int y = height - yOffset - (int) (histo.getCount(histo.getStop()) / (double) maxCount * height * 0.9);
		Point previousPoint = new Point(x, y);

		graphics.setColor(Color.GRAY);
		graphics.drawLine(0, height - yOffset, width, height - yOffset);

		graphics.setColor(histogramColor);

		for (final double key : histo.keySet()) {
			x = (int) (key / delta * width + xOffsetPixels) % width;
			y = height - yOffset - (int) (histo.getCount(key) / (double) maxCount * height * 0.9);
			if (x > previousPoint.x) {
				graphics.drawLine(previousPoint.x, previousPoint.y, x, y);
			}
			previousPoint = new Point(x, y);
		}

		// draw markers

		for (int i = markerPositions.size() / 2; i < markerPositions.size(); i++) {
			double position = markerPositions.get(i);
			x = (int) (position / delta * width + xOffsetPixels) % width;
			y = height - PitchClassHistogramLayer.Y_BORDER
					- (int) (histo.getCount(position) / (double) maxCount * height * 0.9);
			graphics.setColor(Color.BLUE);
			graphics.drawOval(x, y, 2, 2);
		}

		LOG.finer("Histogram layer redrawn.");

	}

	public double getXOffset() {
		return mouseDrag.calculateXOffset();
	}

	double[] scale;
	AudioFile audioFile;

	public void scaleChanged(final double[] newScale, final boolean isChanging, boolean shiftHisto) {
		if (!isChanging) {
			scale = newScale;
		}
	}

	public void audioFileChanged(final AudioFile newAudioFile) {
		audioFile = newAudioFile;
	}

	private JComponent ui;
	private int windowSizePeakDetection = 5;
	private int thresholdPeakDetection = 15;
	
	private void doPeakDetection(boolean detectorIsAdjusting){
		if (histo.getMaxBinCount() != 0) {
			final List<Peak> peaks = PeakDetector.detect(histo, windowSizePeakDetection,thresholdPeakDetection);
			final double[] peaksInCents = new double[peaks.size()];
			int i = 0;
			for (final Peak peak : peaks) {
				peaksInCents[i++] = peak.getPosition();
			}
			Arrays.sort(peaksInCents);
			scaleChangedPublisher.scaleChanged(peaksInCents, detectorIsAdjusting, false);
		}
	}

	public Component ui() {
		if (ui == null) {

			JSlider probabilitySlider = new JSlider(0, 100);
			probabilitySlider.setValue(0);
			probabilitySlider.setMajorTickSpacing(1);
			probabilitySlider.addChangeListener(new ChangeListener() {

				public void stateChanged(final ChangeEvent e) {
					final JSlider source = (JSlider) e.getSource();
					final double newMinProbability = source.getValue() / 100.0;
					AnnotationPublisher.getInstance().clear();
					AnnotationPublisher.getInstance().alterSelection(newMinProbability);
					AnnotationPublisher.getInstance().delegateAddAnnotations(newMinProbability);
					
				}
			});

			JSlider peakSlider = new JSlider(0, 100);
			peakSlider.setValue(windowSizePeakDetection);
			peakSlider.setMajorTickSpacing(20);
			peakSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(final ChangeEvent e) {
					final JSlider source = (JSlider) e.getSource();
					windowSizePeakDetection = source.getValue();
					doPeakDetection(source.getValueIsAdjusting());
				}
			});
			
			JSlider quantizeToScaleSlider = new JSlider(0, 150);
			quantizeToScaleSlider.setValue(15);
			quantizeToScaleSlider.setMajorTickSpacing(20);
			quantizeToScaleSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(final ChangeEvent e) {
					final JSlider source = (JSlider) e.getSource();
					final double cents = source.getValue(); //cents
					AnnotationPublisher.getInstance().applyPitchClassFilter(scale, cents);
				}
			});
				
			
			final JSlider centsSlider = new JSlider(0, 1200);
			centsSlider.setValue(thresholdPeakDetection);
			centsSlider.setMajorTickSpacing(20);			
			final JSlider timingSlider = new JSlider(0, 500);
			timingSlider.setValue(100);
			timingSlider.setMajorTickSpacing(20);								
			ChangeListener steadyStateChangeListener = new ChangeListener() {
				public void stateChanged(final ChangeEvent e) {
					final double cents = centsSlider.getValue(); //cents
					final double time = timingSlider.getValue() / 1000.0; //seconds
					AnnotationPublisher.getInstance().applySteadyStateFilter(cents,time);
				}
			};
			centsSlider.addChangeListener(steadyStateChangeListener);	
			timingSlider.addChangeListener(steadyStateChangeListener);
			
			
			JSlider thresholdSlider = new JSlider(0, 100);
			thresholdSlider.setValue(thresholdPeakDetection);
			thresholdSlider.setMajorTickSpacing(20);
			thresholdSlider.addChangeListener(new ChangeListener() {
				public void stateChanged(final ChangeEvent e) {
					final JSlider source = (JSlider) e.getSource();
					thresholdPeakDetection = source.getValue();
					doPeakDetection(source.getValueIsAdjusting());
				}
			});

			JButton smoothButton = new JButton("Gaussian");
			smoothButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					histo.gaussianSmooth(0.8);
					parent.repaint();
				}
			});

			JButton resetButton = new JButton("Reset");
			resetButton.addActionListener(new ActionListener() {
				public void actionPerformed(ActionEvent e) {
					histo.clear();
					parent.repaint();
				}
			});


			FormLayout layout = new FormLayout("right:min,2dlu,min:grow");
			DefaultFormBuilder builder = new DefaultFormBuilder(layout);
			builder.setDefaultDialogBorder();
			builder.setRowGroupingEnabled(true);
			builder.append("Peakpicking window:", peakSlider, true);
			builder.append("Peakpicking threshold:", thresholdSlider, true);
			builder.append("Time:", timingSlider, true);
			builder.append("Cents:", centsSlider, true);
			builder.append("Quality:", probabilitySlider, true);
			builder.append("Smooth:", smoothButton, true);
			builder.append("Reset:", resetButton, true);
			builder.append("Quantize:", quantizeToScaleSlider, true);
			

			ui = builder.getPanel();
			ui.setInheritsPopupMenu(true);
			ui.setBorder(new TitledBorder("Histogram commands"));

		}
		return ui;
	}
}
