/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ro.nextreports.engine.chart;

import java.awt.Color;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.CategoryLabelPositions;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PiePlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.category.AreaRenderer;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.Range;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.jfree.ui.HorizontalAlignment;
import org.jfree.chart.labels.StandardCategoryItemLabelGenerator;
import org.jfree.chart.labels.StandardXYItemLabelGenerator;
import org.jfree.chart.labels.StandardPieSectionLabelGenerator;

import ro.nextreports.engine.exporter.exception.NoDataFoundException;
import ro.nextreports.engine.exporter.util.function.AbstractGFunction;
import ro.nextreports.engine.exporter.util.function.FunctionFactory;
import ro.nextreports.engine.exporter.util.function.FunctionUtil;
import ro.nextreports.engine.exporter.util.function.GFunction;
import ro.nextreports.engine.queryexec.QueryException;
import ro.nextreports.engine.queryexec.QueryResult;
import ro.nextreports.engine.util.StringUtil;
import ro.nextreports.engine.util.chart.CylinderRenderer;
import ro.nextreports.engine.util.chart.Star;

public class JFreeChartExporter implements ChartExporter {
	
	private QueryResult result;
    private Chart chart;    
    private String chartImageName;
    private Object lastXObjValue = null;    
    private boolean integerXValue = true;
    private DefaultCategoryDataset barDataset;
    private DefaultPieDataset pieDataset;
    private String path;    
    private int width;
    private int height;
    private Map<String, Object> parameterValues;
    private final String DEFAULT_LEGEND_PREFIX = "_L_";
    private static final int DEFAULT_WIDTH = 500;
    private static final int DEFAULT_HEIGHT = 300;
    private Map<String, Integer> xValueSerie = new HashMap<String, Integer>();
    private float transparency = 0.7f;
    
    public JFreeChartExporter(Map<String, Object> parameterValues, QueryResult result, Chart chart) {
    	this(parameterValues, result, chart, ".", DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
    
    public JFreeChartExporter(Map<String, Object> parameterValues, QueryResult result, Chart chart, String path) {
    	this(parameterValues, result, chart, path, DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }
    
    public JFreeChartExporter(Map<String, Object> parameterValues, QueryResult result, Chart chart, String path, 
    		int width, int height) {
    	
    	this(parameterValues, result, chart, path, null, width, height);
    }
    
    public JFreeChartExporter(Map<String, Object> parameterValues, QueryResult result, Chart chart, String path, 
    		String imageName, int width, int height) {
    	
    	if (width <= 0) {
    		width = DEFAULT_WIDTH;
    	}
    	if (height <= 0) {
    		height = DEFAULT_HEIGHT;
    	}
    	this.parameterValues = parameterValues;
        this.result = result;
        this.chart = chart;
        this.path = path;
        this.chartImageName = imageName;
        this.width = width;
        this.height = height;
    }

	public boolean export() throws QueryException, NoDataFoundException {
		testForData();
        createImage();
        return true;
	}
	
	private void testForData() throws QueryException, NoDataFoundException {
        // for procedure call we do not know the row count (is -1)
        if (result == null || result.getColumnCount() <= 0 || result.getRowCount() == 0) {
            throw new NoDataFoundException();
        }
    }
	
	private void createImage() throws QueryException {
		byte type = chart.getType().getType();  
		JFreeChart jfreechart = null;
        if (ChartType.LINE == type) {
        	jfreechart = createLineChart();
        } else if (ChartType.BAR == type) {
        	jfreechart = createBarChart(false, false);
        } else if (ChartType.HORIZONTAL_BAR == type) {
        	jfreechart = createBarChart(true, false);
        } else if (ChartType.STACKED_BAR == type) {
        	jfreechart = createBarChart(false, true);
        } else if (ChartType.PIE == type) {
        	jfreechart = createPieChart();
        } else if (ChartType.AREA == type) {
        	jfreechart = createAreaChart();
        } 
        try {        	
        	if ((chartImageName == null) || "".equals(chartImageName.trim())) {
        		chartImageName = "chart_" + System.currentTimeMillis() + ".jpg";
        	} 
            ChartUtilities.saveChartAsJPEG(new File(path + File.separator + chartImageName), jfreechart, width, height);
        } catch (IOException e) {
            System.err.println("Problem occurred creating chart.");
        }
	}
	
	private JFreeChart createLineChart() throws QueryException {
		XYSeriesCollection dataset = new XYSeriesCollection(); 
		String chartTitle = replaceParameters(chart.getTitle().getTitle());
		Object[] charts = new Object[chart.getYColumns().size()];
		List<String> legends = chart.getYColumnsLegends();
		boolean hasLegend = false;
		for (int i = 0; i < charts.length; i++) {
			String legend = "";
			try {
				legend = replaceParameters(legends.get(i));
			} catch (IndexOutOfBoundsException ex){
				// no legend set
			}
			if ((legend != null) && !"".equals(legend.trim())) {
				hasLegend = true;
			}
			XYSeries lineChart = new XYSeries(legend);			
			charts[i] = lineChart;
			dataset.addSeries(lineChart);
		}
		
		JFreeChart jfreechart = ChartFactory.createXYLineChart(
				chartTitle, // Title
				replaceParameters(chart.getXLegend().getTitle()), // x-axis Label
				replaceParameters(chart.getYLegend().getTitle()), // y-axis Label
                dataset, // Dataset
                PlotOrientation.VERTICAL, // Plot Orientation
                true, // Show Legend
                true, // Use tooltips
                false // Configure chart to generate URLs?
            );
		
		// hide legend if necessary
		if (!hasLegend) {
			jfreechart.removeLegend();
		}
		
		// hide border
		jfreechart.setBorderVisible(false);
		
		// title
		setTitle(jfreechart);
		
		// charts colors & values 
		boolean showValues = (chart.getShowYValuesOnChart() == null) ? false : chart.getShowYValuesOnChart();
		DecimalFormat decimalFormat;
		DecimalFormat percentageFormat;
		if (chart.getYTooltipPattern() == null) {
			decimalFormat = new DecimalFormat("#");
			percentageFormat = new DecimalFormat("0.00%");
		} else {
			decimalFormat = new DecimalFormat(chart.getYTooltipPattern());
			percentageFormat = decimalFormat;
		}
		XYPlot plot = (XYPlot) jfreechart.getPlot();		
		for (int i = 0; i < charts.length; i++) {
			plot.getRenderer().setSeriesPaint(i, chart.getForegrounds().get(i));
			if (showValues) {
				plot.getRenderer().setSeriesItemLabelsVisible(i, true); 
				plot.getRenderer().setSeriesItemLabelGenerator(i, new StandardXYItemLabelGenerator("{2}", decimalFormat, percentageFormat));
			}
		}
		
		if (showValues) {
			// increase a little bit the range axis to view all item label values over points
			plot.getRangeAxis().setUpperMargin(0.2);
		}
				
		// grid axis visibility & colors 
		if ((chart.getXShowGrid() != null) && !chart.getXShowGrid()) {			
			plot.setDomainGridlinesVisible(false);                           
        } else {        	
        	if (chart.getXGridColor() != null) {        		
        		plot.setDomainGridlinePaint(chart.getXGridColor());
        	} else {        		
        		plot.setDomainGridlinePaint(Color.BLACK);
        	}
        }
        if ((chart.getYShowGrid() != null) && !chart.getYShowGrid()) {
        	plot.setRangeGridlinesVisible(false);
        } else {        	
        	if (chart.getYGridColor() != null) {
        		plot.setRangeGridlinePaint(chart.getYGridColor());
        	} else {
        		plot.setRangeGridlinePaint(Color.BLACK);
        	}
        }
       
        // chart background
        plot.setBackgroundPaint(chart.getBackground());                
        
        // labels color
        plot.getDomainAxis().setTickLabelPaint(chart.getXColor());
        plot.getRangeAxis().setTickLabelPaint(chart.getYColor());
        
        //legend color
        plot.getDomainAxis().setLabelPaint(chart.getXLegend().getColor());
        plot.getRangeAxis().setLabelPaint(chart.getYLegend().getColor());
        
        // legend font
        plot.getDomainAxis().setLabelFont(chart.getXLegend().getFont());
        plot.getRangeAxis().setLabelFont(chart.getYLegend().getFont());
        
        // hide labels
        if ((chart.getXShowLabel() != null) && !chart.getXShowLabel()) {        	
        	plot.getDomainAxis().setTickLabelsVisible(false);
        	plot.getDomainAxis().setTickMarksVisible(false);
        }
        if ((chart.getYShowLabel() != null) && !chart.getYShowLabel()) {
        	plot.getRangeAxis().setTickLabelsVisible(false);
        	plot.getRangeAxis().setTickMarksVisible(false);
        }       
        
        // label orientation 
        if (chart.getXorientation() == Chart.VERTICAL) {
        	plot.getDomainAxis().setVerticalTickLabels(true);
        }      
        
        // labels fonts
        plot.getDomainAxis().setTickLabelFont(chart.getXLabelFont());
        plot.getRangeAxis().setTickLabelFont(chart.getYLabelFont());        
        
        // point style
        Shape pointShape = null;
        byte style = chart.getType().getStyle();
        switch (style) {
            case ChartType.STYLE_LINE_DOT_SOLID:               
            case ChartType.STYLE_LINE_DOT_HOLLOW:
            	pointShape = new Ellipse2D.Float(-3.0f, -3.0f, 6.0f, 6.0f);
                break;
            case ChartType.STYLE_LINE_DOT_ANCHOR:  // triangle
            	GeneralPath s5 = new GeneralPath();
            	s5.moveTo(0.0f, -3.0f);
            	s5.lineTo(3.0f, 3.0f);
            	s5.lineTo(-3.0f, 3.0f);
            	s5.closePath(); 
            	pointShape = s5;
                break;
            case ChartType.STYLE_LINE_DOT_BOW:
            	GeneralPath s4 = new GeneralPath();
            	s4.moveTo(-3.0f, -3.0f);
            	s4.lineTo(3.0f, -3.0f);
            	s4.lineTo(-3.0f, 3.0f);
            	s4.lineTo(3.0f, 3.0f);
            	s4.closePath(); 
            	pointShape = s4;
                break;
            case ChartType.STYLE_LINE_DOT_STAR:
                pointShape = new Star(-3.0f, 0f).getShape();
                break;
            default:
            	// no shape
                break;
        }

		if (pointShape != null) {
			XYLineAndShapeRenderer renderer = (XYLineAndShapeRenderer) plot.getRenderer();	
			renderer.setUseFillPaint(true);
			for (int i = 0; i < charts.length; i++) {
				renderer.setSeriesShapesVisible(i, true);				
				if (style != ChartType.STYLE_LINE_DOT_SOLID) {
					renderer.setSeriesFillPaint(i, chart.getBackground());					
				} else {
					renderer.setSeriesFillPaint(i, chart.getForegrounds().get(i));		
				}
				renderer.setSeriesShape(i, pointShape);
			}
		}
        
        final HashMap<String, String> formatValues = createChart(plot.getRangeAxis(), charts);
                
        // in x axis does not contain number values , values are strings representing one unit 
        if (!integerXValue) {
        	((NumberAxis)plot.getDomainAxis()).setTickUnit(new NumberTickUnit(1));
        	((NumberAxis)plot.getDomainAxis()).setNumberFormatOverride(new DecimalFormat(){
    			@Override
    			public StringBuffer format(double number, StringBuffer result, FieldPosition fieldPosition) {						
    				String s = formatValues.get(String.valueOf(Math.round(number)));	    				
    				if (s == null) {
    					s = "";
    				}
    				return result.append(s);
    			}        	
            });
        }                  
        
        return jfreechart;
               
	}
	
	private JFreeChart createBarChart(boolean horizontal, boolean stacked) throws QueryException {
		barDataset = new DefaultCategoryDataset();
		String chartTitle = replaceParameters(chart.getTitle().getTitle());
		Object[] charts = new Object[chart.getYColumns().size()];
		List<String> legends = chart.getYColumnsLegends();
		boolean hasLegend = false;
		for (int i = 0; i < charts.length; i++) {			
			String legend = "";
			try {
				legend = replaceParameters(legends.get(i));
			} catch (IndexOutOfBoundsException ex){
				// no legend set
			}			
			// Important : must have default different legends used in barDataset.addValue
			if ((legend == null) || "".equals(legend.trim())) {
				legend = DEFAULT_LEGEND_PREFIX + String.valueOf(i+1);
			} else {
				hasLegend = true;
			}
			charts[i] = legend;			
		}		
						
		byte style = chart.getType().getStyle();
		JFreeChart jfreechart;
						
		PlotOrientation plotOrientation = horizontal ? PlotOrientation.HORIZONTAL : PlotOrientation.VERTICAL;
		if (stacked) {
			jfreechart = ChartFactory.createStackedBarChart(
    				chartTitle,                    // chart title
    				replaceParameters(chart.getXLegend().getTitle()), // x-axis Label
    				replaceParameters(chart.getYLegend().getTitle()), // y-axis Label
    	            barDataset,                    // data
    	            plotOrientation,               // orientation
    	            true,                          // include legend
    	            true,                          // tooltips
    	            false                          // URLs
    	        );
		} else {
			switch (style) {
			case ChartType.STYLE_BAR_PARALLELIPIPED:
			case ChartType.STYLE_BAR_CYLINDER:
				jfreechart = ChartFactory.createBarChart3D(
						chartTitle,                    // chart title
						replaceParameters(chart.getXLegend().getTitle()), // x-axis Label
						replaceParameters(chart.getYLegend().getTitle()), // y-axis Label
						barDataset,                    // data
						plotOrientation,               // orientation
						true,                          // include legend
						true,                          // tooltips
						false                          // URLs
						);
				break;
			default:
				jfreechart = ChartFactory.createBarChart(
						chartTitle,                    // chart title
						replaceParameters(chart.getXLegend().getTitle()), // x-axis Label
						replaceParameters(chart.getYLegend().getTitle()), // y-axis Label
						barDataset,                    // data
						plotOrientation,               // orientation
						true,                          // include legend
						true,                          // tooltips
						false                          // URLs
						);
				break;
			}
		}
		
		if (style == ChartType.STYLE_BAR_CYLINDER) {
			((CategoryPlot)jfreechart.getPlot()).setRenderer(new CylinderRenderer());
		}
						
		// hide legend if necessary
		if (!hasLegend) {
			jfreechart.removeLegend();
		}
		
		// hide border
		jfreechart.setBorderVisible(false);
		
		// title
		setTitle(jfreechart);
		
		// chart colors & values shown on bars
		boolean showValues = (chart.getShowYValuesOnChart() == null) ? false : chart.getShowYValuesOnChart();
		CategoryPlot plot = (CategoryPlot)jfreechart.getPlot();		
		plot.setForegroundAlpha(transparency);
		BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setDrawBarOutline(false);
        DecimalFormat decimalformat;
        DecimalFormat percentageFormat;
        if (chart.getYTooltipPattern() == null) {
        	decimalformat = new DecimalFormat("#");
        	percentageFormat = new DecimalFormat("0.00%");
		} else {
			decimalformat = new DecimalFormat(chart.getYTooltipPattern());
			percentageFormat = decimalformat;
		}
        for (int i = 0; i < charts.length; i++) {
			renderer.setSeriesPaint(i, chart.getForegrounds().get(i));
			if (showValues) {
				renderer.setSeriesItemLabelsVisible(i, true); 
				renderer.setSeriesItemLabelGenerator(i, new StandardCategoryItemLabelGenerator("{2}", decimalformat, percentageFormat));
			}
		}   
        
        if (showValues) {
        	// increase a little bit the range axis to view all item label values over bars
        	plot.getRangeAxis().setUpperMargin(0.2);
        }
        
        // grid axis visibility & colors 
        if ((chart.getXShowGrid() != null) && !chart.getXShowGrid()) {			
			plot.setDomainGridlinesVisible(false);                           
        } else {        	
        	if (chart.getXGridColor() != null) {        		
        		plot.setDomainGridlinePaint(chart.getXGridColor());
        	} else {        		
        		plot.setDomainGridlinePaint(Color.BLACK);
        	}
        }
        if ((chart.getYShowGrid() != null) && !chart.getYShowGrid()) {
        	plot.setRangeGridlinesVisible(false);
        } else {        	
        	if (chart.getYGridColor() != null) {
        		plot.setRangeGridlinePaint(chart.getYGridColor());
        	} else {
        		plot.setRangeGridlinePaint(Color.BLACK);
        	}
        }
       
        // chart background
        plot.setBackgroundPaint(chart.getBackground());                
        
        // labels color
        plot.getDomainAxis().setTickLabelPaint(chart.getXColor());
        plot.getRangeAxis().setTickLabelPaint(chart.getYColor());
        
        // legend color
        plot.getDomainAxis().setLabelPaint(chart.getXLegend().getColor());
        plot.getRangeAxis().setLabelPaint(chart.getYLegend().getColor());
        
        // legend font
        plot.getDomainAxis().setLabelFont(chart.getXLegend().getFont());
        plot.getRangeAxis().setLabelFont(chart.getYLegend().getFont());
        
        // axis color
        plot.getDomainAxis().setAxisLinePaint(chart.getxAxisColor());
        plot.getRangeAxis().setAxisLinePaint(chart.getyAxisColor());
        
        // hide labels
        if ((chart.getXShowLabel() != null) && !chart.getXShowLabel()) {        	
        	plot.getDomainAxis().setTickLabelsVisible(false);
        	plot.getDomainAxis().setTickMarksVisible(false);
        }
        if ((chart.getYShowLabel() != null) && !chart.getYShowLabel()) {
        	plot.getRangeAxis().setTickLabelsVisible(false);
        	plot.getRangeAxis().setTickMarksVisible(false);
        }       
        
        // label orientation
        CategoryAxis domainAxis = plot.getDomainAxis();        
        if (chart.getXorientation() == Chart.VERTICAL) {
        	domainAxis.setCategoryLabelPositions(CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 2));
        } else if (chart.getXorientation() == Chart.DIAGONAL) {
        	domainAxis.setCategoryLabelPositions(CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 4));
        } else if (chart.getXorientation() == Chart.HALF_DIAGONAL) {
        	domainAxis.setCategoryLabelPositions(CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 8));
        }            
        
        // labels fonts
        domainAxis.setTickLabelFont(chart.getXLabelFont());
        plot.getRangeAxis().setTickLabelFont(chart.getYLabelFont());        
		
		createChart(plot.getRangeAxis(), charts);              
		
		return jfreechart;
	}
	
	
	private JFreeChart createAreaChart() throws QueryException {
		barDataset = new DefaultCategoryDataset();
		String chartTitle = replaceParameters(chart.getTitle().getTitle());
		Object[] charts = new Object[chart.getYColumns().size()];
		List<String> legends = chart.getYColumnsLegends();
		boolean hasLegend = false;
		for (int i = 0; i < charts.length; i++) {			
			String legend = "";
			try {
				legend = replaceParameters(legends.get(i));
			} catch (IndexOutOfBoundsException ex){
				// no legend set
			}			
			// Important : must have default different legends used in barDataset.addValue
			if ((legend == null) || "".equals(legend.trim())) {
				legend = DEFAULT_LEGEND_PREFIX + String.valueOf(i+1);
			} else {
				hasLegend = true;
			}			
			charts[i] = legend;			
		}		
						
		byte style = chart.getType().getStyle();
		JFreeChart jfreechart = ChartFactory.createAreaChart(
	            "Area Chart",             // chart title
	            replaceParameters(chart.getXLegend().getTitle()), // x-axis Label
				replaceParameters(chart.getYLegend().getTitle()), // y-axis Label
	            barDataset,               // data
	            PlotOrientation.VERTICAL, // orientation
	            true,                     // include legend
	            true,                     // tooltips
	            false                     // urls
	        );
					
		// hide legend if necessary
		if (!hasLegend) {			
			jfreechart.removeLegend();
		}
		
		// hide border
		jfreechart.setBorderVisible(false);
		
		// title
		setTitle(jfreechart);
		
		// chart colors & values shown on bars
		boolean showValues = (chart.getShowYValuesOnChart() == null) ? false : chart.getShowYValuesOnChart();
		CategoryPlot plot = (CategoryPlot)jfreechart.getPlot();		
		plot.setForegroundAlpha(transparency);
		AreaRenderer renderer = (AreaRenderer) plot.getRenderer();        
        DecimalFormat decimalformat;   
        DecimalFormat percentageFormat;
        if (chart.getYTooltipPattern() == null) {
        	decimalformat = new DecimalFormat("#");
        	percentageFormat = new DecimalFormat("0.00%");
		} else {
			decimalformat = new DecimalFormat(chart.getYTooltipPattern());
			percentageFormat = decimalformat;
		}
        for (int i = 0; i < charts.length; i++) {        	
			renderer.setSeriesPaint(i, chart.getForegrounds().get(i));
			if (showValues) {
				renderer.setSeriesItemLabelsVisible(i, true); 
				renderer.setSeriesItemLabelGenerator(i, new StandardCategoryItemLabelGenerator("{2}", decimalformat, percentageFormat));
			}
		}   
        
        if (showValues) {
        	// increase a little bit the range axis to view all item label values over bars
        	plot.getRangeAxis().setUpperMargin(0.2);
        }
        
        // grid axis visibility & colors 
        if ((chart.getXShowGrid() != null) && !chart.getXShowGrid()) {			
			plot.setDomainGridlinesVisible(false);                           
        } else {        	
        	if (chart.getXGridColor() != null) {        		
        		plot.setDomainGridlinePaint(chart.getXGridColor());
        	} else {        		
        		plot.setDomainGridlinePaint(Color.BLACK);
        	}
        }
        if ((chart.getYShowGrid() != null) && !chart.getYShowGrid()) {
        	plot.setRangeGridlinesVisible(false);
        } else {        	
        	if (chart.getYGridColor() != null) {
        		plot.setRangeGridlinePaint(chart.getYGridColor());
        	} else {
        		plot.setRangeGridlinePaint(Color.BLACK);
        	}
        }
       
        // chart background
        plot.setBackgroundPaint(chart.getBackground());                
        
        // labels color
        plot.getDomainAxis().setTickLabelPaint(chart.getXColor());
        plot.getRangeAxis().setTickLabelPaint(chart.getYColor());
        
        // legend color
        plot.getDomainAxis().setLabelPaint(chart.getXLegend().getColor());
        plot.getRangeAxis().setLabelPaint(chart.getYLegend().getColor());
        
        // legend font
        plot.getDomainAxis().setLabelFont(chart.getXLegend().getFont());
        plot.getRangeAxis().setLabelFont(chart.getYLegend().getFont());
        
        // axis color
        plot.getDomainAxis().setAxisLinePaint(chart.getxAxisColor());
        plot.getRangeAxis().setAxisLinePaint(chart.getyAxisColor());
        
        // hide labels
        if ((chart.getXShowLabel() != null) && !chart.getXShowLabel()) {        	
        	plot.getDomainAxis().setTickLabelsVisible(false);
        	plot.getDomainAxis().setTickMarksVisible(false);
        }
        if ((chart.getYShowLabel() != null) && !chart.getYShowLabel()) {
        	plot.getRangeAxis().setTickLabelsVisible(false);
        	plot.getRangeAxis().setTickMarksVisible(false);
        }       
        
        // label orientation
        CategoryAxis domainAxis = plot.getDomainAxis();        
        if (chart.getXorientation() == Chart.VERTICAL) {
        	domainAxis.setCategoryLabelPositions(CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 2));
        } else if (chart.getXorientation() == Chart.DIAGONAL) {
        	domainAxis.setCategoryLabelPositions(CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 4));
        } else if (chart.getXorientation() == Chart.HALF_DIAGONAL) {
        	domainAxis.setCategoryLabelPositions(CategoryLabelPositions.createUpRotationLabelPositions(Math.PI / 8));
        }     
        
        // labels fonts
        plot.getDomainAxis().setTickLabelFont(chart.getXLabelFont());
        plot.getRangeAxis().setTickLabelFont(chart.getYLabelFont());      
		
		createChart(plot.getRangeAxis(), charts);              
		
		return jfreechart;
	}
	
	
	private JFreeChart createPieChart() throws QueryException {		
		pieDataset = new DefaultPieDataset();
		String chartTitle = replaceParameters(chart.getTitle().getTitle());
		JFreeChart jfreechart = ChartFactory.createPieChart(
				    chartTitle,
	                pieDataset, 
	                true, 
	                true, 
	                false);
		
		// hide border
		jfreechart.setBorderVisible(false);
		
		// title
		setTitle(jfreechart);
		
		PiePlot plot = (PiePlot)jfreechart.getPlot();
		plot.setForegroundAlpha(transparency);
		// a start angle used to create similarities between flash chart and this jfreechart
		plot.setStartAngle(330);	
		// legend label will contain the text and the value
		plot.setLegendLabelGenerator(new StandardPieSectionLabelGenerator("{0} = {1}"));
		
		DecimalFormat decimalformat;
		DecimalFormat percentageFormat;
		if (chart.getYTooltipPattern() == null) {
        	decimalformat = new DecimalFormat("#");
        	percentageFormat = new DecimalFormat("0.00%");
		} else {
			decimalformat = new DecimalFormat(chart.getYTooltipPattern());
			percentageFormat = decimalformat;
		}
		boolean showValues = (chart.getShowYValuesOnChart() == null) ? false : chart.getShowYValuesOnChart();
		if (showValues) {
			// label will contain also the percentage formatted with two decimals
			plot.setLabelGenerator(new StandardPieSectionLabelGenerator("{0} ({2})", decimalformat, percentageFormat));
		}
		
		// chart background
        plot.setBackgroundPaint(chart.getBackground());
                                                      
        createChart(null, new Object[1]);   
        
        // after chart creation we can set slices colors
        List <Comparable> keys = pieDataset.getKeys();
        List<Color> colors = chart.getForegrounds();
        for (int i = 0, size = colors.size(); i < keys.size(); i++) {            	
            plot.setSectionPaint(keys.get(i), colors.get(i % size));
            plot.setLabelFont(chart.getFont());
        }
		
		return jfreechart;
	}
	
	private void setTitle(JFreeChart jfreechart) {
		TextTitle title = new TextTitle(replaceParameters(chart.getTitle().getTitle()));
		title.setFont(chart.getTitle().getFont());
		title.setPaint(chart.getTitle().getColor());
		if  (chart.getTitle().getAlignment() == ChartTitle.LEFT_ALIGNMENT) {
			title.setHorizontalAlignment(HorizontalAlignment.LEFT);
		} else if  (chart.getTitle().getAlignment() == ChartTitle.RIGHT_ALIGNMENT) {
			title.setHorizontalAlignment(HorizontalAlignment.RIGHT);
		} else {
			title.setHorizontalAlignment(HorizontalAlignment.CENTER);
		}
		jfreechart.setTitle(title);
	}
	
	private HashMap<String, String> createChart(ValueAxis rangeAxis, Object[] charts) throws QueryException {       
        int row = 0;
        Object previous = null;
        String xColumn = chart.getXColumn();
        String xPattern = chart.getXPattern();          
        String lastXValue = "";
        HashMap<String, String> formatValues = new HashMap<String, String>();      
        Number min = Double.MAX_VALUE;
        Number max = Double.MIN_VALUE;
        
        int chartsNo = charts.length;        
        int[] index = new int[chartsNo];
        GFunction[] functions = new GFunction[chartsNo];   
        for (int i = 0; i < chartsNo; i++) {
            functions[i] = FunctionFactory.getFunction(chart.getYFunction());
            index[i] = 1;
        }
        boolean isStacked = (ChartType.STACKED_BAR == chart.getType().getType());
        
        while (result.hasNext()) {
        	
            Object[] objects = new Object[chartsNo];
            Number[] computedValues = new Number[chartsNo];            
            for (int i = 0; i < chartsNo; i++) {
                if (chart.getYColumns().get(i) != null) {
                    objects[i] = result.nextValue(chart.getYColumns().get(i));
                    Number value = null;
                    if (objects[i] instanceof Number) {
                        value = (Number) objects[i];
                    } else if (objects[i] != null){
                        value = 1;
                        integerXValue = false;
                    } else {
                    	value = 0;
                    }
                    computedValues[i] = value;
                }
            }

            Object xValue = null;                
            if (row == 0) {
                xValue = result.nextValue(xColumn);          
                lastXObjValue = xValue;
                lastXValue = getStringValue(xColumn, xPattern);
            } else {
                xValue = previous;
            }            
            Object newXValue = result.nextValue(xColumn);            

            boolean add = false;
            int position = 0;
            // no function : add the value
            if (AbstractGFunction.NOOP.equals(functions[0].getName())) {        
            	lastXValue = getStringValue(xColumn, xPattern);
                add = true;
                // compute function
            } else {
                boolean equals = FunctionUtil.parameterEquals(xValue, newXValue);                
                if (equals) {
                    for (int i = 0; i < chartsNo; i++) {
                        functions[i].compute(objects[i]);
                    }
                } else {
                    for (int i = 0; i < chartsNo; i++) {
                    	position = i;
                        add = true;
                        computedValues[i] = (Number) functions[i].getComputedValue();
                        functions[i].reset();
                        functions[i].compute(objects[i]);
                    }
                }
            }
            
            if (add) {   
            	Number n;
            	Number sum = 0;            	
            	if (xValue instanceof Number) {
            		n = (Number)newXValue;
            	} else {
            		integerXValue = false;
            		n = index[position]++;
            	}
                for (int i = 0; i < chartsNo; i++) {                  	
                    addValue(charts[i], n, lastXValue, computedValues[i], formatValues);   
                    if (!isStacked) {
                    	min = Math.min(min.doubleValue(), computedValues[i].doubleValue());
                    	max = Math.max(max.doubleValue(), computedValues[i].doubleValue());
                    } else {
                    	sum = sum.doubleValue() + computedValues[i].doubleValue();
                    }
                }       
                if (isStacked) {
                    min = 0;
                    max = Math.max(max.doubleValue(), sum.doubleValue());
                }
                lastXValue = getStringValue(xColumn, xPattern);                
            }
            row++;
            previous = newXValue;            
        }

        // last group
        if (!AbstractGFunction.NOOP.equals(functions[0].getName())) {   
        	Number n;
        	Number sum = 0;
        	if (lastXObjValue instanceof Number) {
        		n = (Number)lastXObjValue;
        	} else {
        		integerXValue = false;
        		n = index[chartsNo-1]++;
        	}
            for (int i = 0; i < chartsNo; i++) {
                Number value = (Number) functions[i].getComputedValue();                
                addValue(charts[i], n, lastXValue, value, formatValues);   
                if (!isStacked) {
                	min = Math.min(min.doubleValue(), value.doubleValue());
                	max = Math.max(max.doubleValue(), value.doubleValue());
                } else {
                	sum = sum.doubleValue() + value.doubleValue();
                }
            }   
            if (isStacked) {
                min = 0;
                max = Math.max(max.doubleValue(), sum.doubleValue());
            }
        }   
        
        setAxisRange(rangeAxis, min, max);
        
        return formatValues;       
    }
	
	// take care if no function is set : every value is in a differnet category (we use an incremental integer : serie)
	private void addValue(Object chartSerie, Number x, String lastXValue, Number y, HashMap<String, String> formatValues) {		
		if (ChartType.LINE == chart.getType().getType()) {		
			
		   ((XYSeries)chartSerie).add(x, y);
		   
		} else if ((ChartType.BAR == chart.getType().getType()) || 
				(ChartType.HORIZONTAL_BAR == chart.getType().getType()) || 
				(ChartType.STACKED_BAR == chart.getType().getType()) || 
				(ChartType.AREA == chart.getType().getType()) ) {
			
			int serie = 0;
			Integer i = xValueSerie.get(lastXValue);
			if (i != null) {
				serie = i+1;
				xValueSerie.put(lastXValue, serie);
				barDataset.setValue(y, (String)chartSerie, lastXValue + " (" + serie + ")");		
			} else {
				barDataset.setValue(y, (String)chartSerie, lastXValue);
				xValueSerie.put(lastXValue, 0);
			}	
			//barDataset.setValue(y, (String)chartSerie, lastXValue);
			
		} else if (ChartType.PIE == chart.getType().getType()) {
			if (AbstractGFunction.NOOP.equals(chart.getYFunction())) {
				int serie = 0;
				Integer i = xValueSerie.get(lastXValue);
				if (i != null) {
					serie = i+1;
					xValueSerie.put(lastXValue, serie);
					pieDataset.setValue(lastXValue + " (" + serie + ")", y);					
				} else {
					pieDataset.setValue(lastXValue, y);
					xValueSerie.put(lastXValue, 0);
				}				
			} else {
				pieDataset.setValue(lastXValue, y);
			}
		}
		formatValues.put(x.toString(), lastXValue);
	}
	
	private String getStringValue(String column, String pattern) throws QueryException {
        Object xObject = result.nextValue(column);
        lastXObjValue = xObject;
        return StringUtil.getValueAsString(xObject, pattern);
    }

	public String getChartImageName() {
		return chartImageName;
	}	
	
	public String getChartImageAbsolutePath() {
		File file = new File(path + File.separator+ chartImageName);
		return file.getAbsolutePath();
	}
	
	public int getWidth() {
		return width;
	}
	
	public int getHeight() {
		return height;
	}
	
	// replace $P{...} parameters (used in title and x,y legends
    private String replaceParameters(String text) {
    	if (text == null) {
    		return null;
    	}
        for  (String param : parameterValues.keySet()) {
             text = StringUtil.replace(text, "\\$P\\{" + param + "\\}", StringUtil.getValueAsString(parameterValues.get(param), null));
        }
        return text;
    }
    
    private void setAxisRange(ValueAxis rangeAxis, Number min, Number max) {
    	if (rangeAxis == null) {
    		return;
    	}
    	
    	YRange yRange = new YRange(min, max);
    	yRange = yRange.update();    	
    	rangeAxis.setRange(new Range(yRange.getMin().doubleValue(), yRange.getMax().doubleValue()));
    }
    
    
	
}
