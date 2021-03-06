package eu.gity.jmeter.webgenerator;

import org.apache.jmeter.JMeter;
import org.apache.jmeter.engine.StandardJMeterEngine;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.Copy;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.report.config.ConfigurationException;
import org.apache.jmeter.report.dashboard.GenerationException;
import org.apache.jmeter.report.dashboard.ReportGenerator;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleSaveConfiguration;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.TestStateListener;
import org.apache.jmeter.threads.JMeterContextService;
import org.apache.jmeter.util.Calculator;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jmeter.visualizers.SamplingStatCalculator;
import org.apache.jmeter.visualizers.gui.AbstractVisualizer;
import org.apache.jorphan.util.JOrphanUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.Serializable;
import java.lang.Cloneable;
import java.net.URI;
import java.net.URISyntaxException;


/**
 * This class generates results to website.
 * Class collecting results during testing and calculates other necessary statistics.
 * At the end of test automatically generates website.
 *
 * @author Jan Benedikt, Gity a.s.
 */
public class WebGenerator extends AbstractVisualizer {
    private static final long serialVersionUID = 240L; // UID of module WebGenerator

    private ArrayList<dataCollector> arrayDataC = new ArrayList<>(); // Object for statistics
    private dataCollector total = new dataCollector(); // Own object for statistics TOTAL

    private static final Logger log = LoggerFactory.getLogger(WebGenerator.class);
    private Map<String, Calculator> tableRows = new ConcurrentHashMap<>();
    private Map<String, SamplingStatCalculator> samplingRows = new ConcurrentHashMap<>();
    private final transient Object lock = new Object(); // Object for threads synchronization
    private final String TOTAL_ROW_LABEL = JMeterUtils.getResString("web_generator_row_total");  //Name of "TOTAL" row in Calculator class

    private JButton generateWebsiteButton; // Button for website generating
    private JTextField textPath; // After website generating is shown here path to the folder with website
    private JCheckBox afterEndGenerateWebsite; // After check automatically generates website
    private JCheckBox checkInclGroupName; // After check is in results included name of thread group
    private JRadioButton chckBrowser;
    private JRadioButton chckFolder;
    private boolean running = false; // Status of JMeter engine if running or not.
    private String reportOutputFolder = ""; // Variable for path to generating website
    private String filePath;  // Variable for path to the folder with csv file
    private boolean generated = false; //Variable for check if webasite was generated

    private static final String webGeneratorGuiClass = (WebGenerator.class).getCanonicalName();

    private static final String WSPATH = "WebGenerator.websitePath";
    private static final String GENAFTE = "WebGenerator.checkGenerateAfterTest";
    private static final String INCLTHN = "WebGenerator.inclThreadGrpName";
    private static final String JMETER_REPORT_TEMPLATE_DIR_PROPERTY =
            "jmeter.reportgenerator.exporter.html.property.template_dir";

    /**
     * {@inheritDoc}
     */
    @Override
    public void modifyTestElement(TestElement element){
        super.modifyTestElement(element);

        element.setProperty(WebGenerator.GENAFTE, afterEndGenerateWebsite.isSelected());
        element.setProperty(WebGenerator.INCLTHN, checkInclGroupName.isSelected());
        element.setProperty(WebGenerator.WSPATH, textPath.getText());

        configureSaving(getModel());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void configure(TestElement element){
        super.configure(element);
        afterEndGenerateWebsite.setSelected(element.getPropertyAsBoolean(WebGenerator.GENAFTE));
        checkInclGroupName.setSelected(element.getPropertyAsBoolean(WebGenerator.INCLTHN));
        textPath.setText(element.getPropertyAsString(WebGenerator.WSPATH));
    }

    /**
     * Constructor of module WebGenerator.
     * In constructor is called method "getInstance" of WebGenerator Tree guard and "clearData". Those methods are for preparation of first run.
     */
    public WebGenerator() {
        super();
        clearData();
        init();
    }

    /**
     * Modifies sample save config to save only necessary sample result fields into the result file
     *
     * @param rc ResultCollector of this visualizer
     */
    private void configureSaving(ResultCollector rc) {
        SampleSaveConfiguration saveConfig = new SampleSaveConfiguration(false);
        saveConfig.setTime(true);  //Elapsed time
        saveConfig.setIdleTime(true); //Iddle time
        saveConfig.setSuccess(true); //Success
        saveConfig.setLabel(true); //Label
        saveConfig.setThreadName(true); //Thread name
        saveConfig.setTimestamp(true); //Timestamp
        saveConfig.setCode(true); // Response code
        saveConfig.setSentBytes(true); //sent byt count
        saveConfig.setMessage(true); // response message?
        saveConfig.setFieldNames(true); //Field names
        saveConfig.setAssertionResultsFailureMessage(true);//Assertion Results Failure Message
        saveConfig.setThreadCounts(true); //Active Thread counts
        saveConfig.setLatency(true); //Latency
        saveConfig.setSampleCount(true); //Sample and error count
        saveConfig.setDataType(true); //Data type
        saveConfig.setBytes(true); //received byte count
        saveConfig.setUrl(true); //URL
        saveConfig.setConnectTime(true); //connect time
        saveConfig.setHostname(true); // hostname
        rc.setSaveConfig(saveConfig);
    }


    /**
     * Get the component's resource name, which getStaticLabel uses to derive
     * the component's label in the local language. The resource name is fixed,
     * and does not vary with the selected language.
     *
     * @return the resource name
     */
    @Override
    public String getLabelResource() {
        return "web_generator_generator";
    }

    /**
     * Initialization of module.
     * This initialization create main GUI of module and run registration.
     */
    protected void init() {
        // WARNING: called from ctor so must not be overridden (i.e. must be private or final)
        // CREATE Main Panel (project name, folder address, etc..)
        setLayout(new BorderLayout());
        setBorder(makeBorder());
        // SHOW Main Panel
        makeUI();
        register();
    }

    /**
     * Registering listener to core of JMeter.
     */
    private void register() {
        TestStateListener listen = new TestStateListener() {
            @Override
            public void testStarted() {
            	WebGeneratorTreeGuard.getInstance().registerListeners();
        		JMeterTreeModel jmeterTreeModel = GuiPackage.getInstance().getTreeModel();
        				
        				ArrayList<JMeterTreeNode> rcNodes = new ArrayList<JMeterTreeNode>(jmeterTreeModel.getNodesOfType(ResultCollector.class));
        				int count = 0;
        				if(rcNodes.size() != 0) {
        					for (JMeterTreeNode rcNode : rcNodes) {
        						if ((rcNode.getTestElement().getPropertyAsString(TestElement.GUI_CLASS).contains("WebGenerator")) 
        							&& (WebGenerator.super.getModel() == rcNode.getTestElement())) {
        							count++;
        						}
        						if (count > 0) { 
        							generated = false;
        			                running = true;
        			                filePath = getFile();

        			                generateWebsiteButton.setEnabled(false);
        			                checkInclGroupName.setEnabled(false);
        			                clearData();
        							
        						}
        					}	
        				}
            }

            @Override
            public void testStarted(String host) {
                testStarted();
            }

            @Override
            public void testEnded() {
            	WebGeneratorTreeGuard.getInstance().registerListeners();
        		JMeterTreeModel jmeterTreeModel = GuiPackage.getInstance().getTreeModel();
        		
        				ArrayList<JMeterTreeNode> rcNodes = new ArrayList<JMeterTreeNode>(jmeterTreeModel.getNodesOfType(ResultCollector.class));
        				int count = 0;
        				if(rcNodes.size() != 0) {
        					for (JMeterTreeNode rcNode : rcNodes) {
        						if ((rcNode.getTestElement().getPropertyAsString(TestElement.GUI_CLASS).contains("WebGenerator")) 
            							&& (WebGenerator.super.getModel() == rcNode.getTestElement())) {
        							count++;
        						}
        						if (count > 0) { 
        							running = false;
        	                        if(!generated) {
        	                        if(!(filePath.equals(null) || filePath.equals("") || (filePath.length() <= 0))) {
        	                            if (afterEndGenerateWebsite.isSelected()) {
        	                                try {
        	                                    filePath = getFile();
        	                                    callGenerator();
        	                                } catch (GenerationException e) {
        	                                    log.error(JMeterUtils.getResString("web_generator_log_problem_generating_website") + " " + e);
        	                                }
        	                            }
        	                            generateWebsiteButton.setEnabled(true);
        	                            checkInclGroupName.setEnabled(true);
        	                            generated = true;
        	                        }
        	                        }
        						}
        					}	
        				}        				
                reRegister();
            }

            @Override
            public void testEnded(String host) {
                testEnded();
            }
        };
        try{
            StandardJMeterEngine.register(listen);
        }catch(Exception e){
            log.error(JMeterUtils.getResString("web_generator_log_cant_register"));
        }

    }

    /**
     * Method for re-register of listener.
     */
    private void reRegister() {
        register();
    }

    /**
     * Method for rendering UI in module WebGenerator.
     */
    private void makeUI() {
        // CREATE Control Panel (properties of generating website)
        JPanel controlPanel = new JPanel();
        controlPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), JMeterUtils.getResString("web_generator_options"))); //$NON-NLS-1$

        SpringLayout layout = new SpringLayout();

        afterEndGenerateWebsite = new JCheckBox(JMeterUtils.getResString("web_generator_auto_generate_website"), false);
        generateWebsiteButton = new JButton(JMeterUtils.getResString("web_generator_generate"));
        checkInclGroupName = new JCheckBox(JMeterUtils.getResString("web_generator_include_thg_name"), false);
        textPath = new JTextField(JMeterUtils.getResString("web_generator_path_to_gen_web"));
        textPath.setSize(afterEndGenerateWebsite.getSize());
        textPath.setEnabled(false);
        generateWebsiteButton.setEnabled(false);

        generateWebsiteButton.addActionListener((ActionEvent e) -> {
            try {
                filePath = getFile();
                callGenerator();
            } catch (GenerationException e1) {
                log.error(JMeterUtils.getResString("web_generator_log_cant_add_listener") + " " + e1);
            }
        });

        JPanel gui = new JPanel(new BorderLayout(5, 5));
        gui.setBorder(new EmptyBorder(3, 3, 3, 3));

        JPanel controls = new JPanel(new BorderLayout(5, 5));

        JPanel buttons = new JPanel(new GridLayout(0, 1, 5, 5));
        buttons.add(checkInclGroupName);
        buttons.add(afterEndGenerateWebsite);
        buttons.add(textPath);
        buttons.add(generateWebsiteButton);
        buttons.setBorder(new TitledBorder(JMeterUtils.getResString("web_generator_options")));

        controls.add(buttons, BorderLayout.NORTH);

        gui.add(controls, BorderLayout.WEST);

        controlPanel.setLayout(layout);
        //setting flow layout of right alignment
        add(makeTitlePanel(), BorderLayout.NORTH);
        add(controlPanel, 1);
        add(gui);

    }

    /**
     * This method gives from path to csv file only path without csv file.
     *
     * @param path path to csv file.
     * @return path to folder.
     */
    private String getFolder(String path) {
        int sep = path.lastIndexOf(File.separatorChar);
        return path.substring(0, sep);
    }

    /**
     * Generate path to folder with future website. This folder is nessesary to have unique name.
     * This unique name is generated from actual date and time.
     * Path to file is passes from getFolder method.
     */
    private void outputFolder() {
        // Create date in format YearMonthDayHourMinute (Simpledateformat - template)
        SimpleDateFormat ft = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Date date = new Date();

        //Creating of final folder:
        reportOutputFolder = (getFolder(filePath) + File.separatorChar + (ft.format(date)));
        textPath.setText(reportOutputFolder);
        textPath.setEnabled(true);
    }

    /**
     * Mathod for dynamically set of granularity in graph. This granularity depends on lenght of testing.
     * Longer test bigger granularity.
     * Shorter test smaller granularity.
     *
     * @param startTimeMillis - Start time of test in miliseconds (unix time).
     * @param endTimeMillis - End time of test in miliseconds (unix time).
     */
    private void setGranularity(int startTimeMillis, int endTimeMillis){

    }

    /**
     * Method for calling Dashboard generator.
     * @throws GenerationException if folder is not empty or doesn't exists.
     */
    private void callGenerator() throws GenerationException {
        outputFolder();
        File reportOutputFolderAsFile = new File(reportOutputFolder);
        //Check of target folder:
        JOrphanUtils.canSafelyWriteToFolder(reportOutputFolderAsFile);
        //Set global variable „.JMETER_REPORT_OUTPUT_DIR_PROPERTY“:
        JMeterUtils.setProperty(JMeter.JMETER_REPORT_OUTPUT_DIR_PROPERTY, reportOutputFolderAsFile.getAbsolutePath());
        //Set JMeter property of path to website template
        String jmeterHomeDir;
        if (System.getProperty("jmeter.home") == null) {
            File userDir = new File(System.getProperty("user.dir"));
            jmeterHomeDir = userDir.getAbsoluteFile().getParent();
        } else {
            jmeterHomeDir = System.getProperty("jmeter.home");
        }
        String pathToTemplate = jmeterHomeDir + "/lib/ext/webgenerator/report_template";
        pathToTemplate = FilenameUtils.separatorsToSystem(pathToTemplate);
        JMeterUtils.setProperty(JMETER_REPORT_TEMPLATE_DIR_PROPERTY, pathToTemplate);

        //Create of instance „ReportGenerator“ (in argument passes way to file with source data):
        ReportGenerator generator = null;
        try {
            generator = new ReportGenerator(filePath, null);
        } catch (ConfigurationException e) {
            log.error(JMeterUtils.getResString("web_generator_log_call_report_generator") + " " + e);
        }
        // Start generating:
        if (generator == null) throw new AssertionError();
        generator.generate();

        //Save data to functions.js

        try {
            editFunctionsJS(arrayDataC);
        } catch (Exception e) {
            log.error(JMeterUtils.getResString("web_generator_log_problem_js_file") + " " + e);
        }

     	if(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
	        try {
	            Desktop.getDesktop().open(new File(reportOutputFolder));
	            try {
					Desktop.getDesktop().browse(new URI(reportOutputFolder + File.separatorChar + "index.html"));
				} catch (URISyntaxException e) {
					log.error(JMeterUtils.getResString("web_generator_browser_error") + "\n" + e);
					e.printStackTrace();
				}
	        } catch (IOException e) {
	            log.error(JMeterUtils.getResString("web_generator_log_open_folder") + " " + e);
	        }
     	}else {
     		Runtime runtime = Runtime.getRuntime();
     		try {
     			runtime.exec("xdg-open " + reportOutputFolder + File.separatorChar + "index.html");
     		}catch (IOException e){
     			log.error(JMeterUtils.getResString("web_generator_browser_error") + "\n" + e);
     		}
     	}
    }

    /**
     * Destructor.
     * Cleaning every variables.
     */
    @Override
    public void clearData() {
        total.setPercentile_90th(0);
        total.setPercentile_95th(0);
        total.setPercentile_99th(0);
        total.setUserCount(0);
        total.setErrorPercent(0);
        total.setMean(0);
        total.setAvgBytes(0);
        total.setRate(0);
        total.setName("");
        total.setMaxDataC(0);
        total.setMinDataC(0);
        total.setStDev(0);
        total.setLatency(0);
        total.setLoopCount(0);
        total.setSentBytes(0);
        total.setConnectTime(0);
        total.setLoopCount(0);
        total.setReceivedBytes(0);
        total.setResponseTime(0);
        total.Comparison = 0;

        arrayDataC.clear();

        synchronized (lock) {
            tableRows.clear();
            tableRows.put(TOTAL_ROW_LABEL, new Calculator(TOTAL_ROW_LABEL));

            samplingRows.clear();
            samplingRows.put(TOTAL_ROW_LABEL, new SamplingStatCalculator(TOTAL_ROW_LABEL));
        }
    }

    /**
     * This method filling object dataCollector with results of testing.
     * @param sample is passes from core of JMeter.
     */
    @Override
    public void add(final SampleResult sample) {
        if(!running) {
            generateWebsiteButton.setEnabled(true);
        }
        final String sampleLabel = sample.getSampleLabel(checkInclGroupName.isSelected());
        Calculator calc; //Initialize method Calculator which collects data of results
        SamplingStatCalculator samplingCalc; //Initialize method samplingCalculator which collects data of percentiles

        synchronized (lock) {
            calc = tableRows.get(sampleLabel);
            samplingCalc = samplingRows.get(sampleLabel);

            if (calc == null) {
                calc = new Calculator(sampleLabel);
                tableRows.put(calc.getLabel(), calc);
            }

            if (samplingCalc == null) {
                samplingCalc = new SamplingStatCalculator(sampleLabel);
                samplingRows.put(samplingCalc.getLabel(), samplingCalc);
            }

            calc.addSample(sample);
            samplingCalc.addSample(sample);

            Calculator tot = tableRows.get(TOTAL_ROW_LABEL);
            SamplingStatCalculator samplingtot = samplingRows.get(TOTAL_ROW_LABEL);

            tot.addSample(sample);
            samplingtot.addSample(sample);
        }
        
       

        dataCollector dataC = new dataCollector();
        dataC.setName(sampleLabel);
        dataC.setLatency(sample.getLatency());
        dataC.setUserCount(sample.getGroupThreads());
        dataC.setResponseTime(sample.getTime());
        dataC.setConnectTime(sample.getConnectTime());

        dataC.Comparison = 0;
        if (dataC.WebAddress.size() != 0) {
            for (int position = 0; position < dataC.WebAddress.size(); position++) {
                if (dataC.WebAddress.get(position).equals(sample.getUrlAsString())) {
                    ++dataC.Comparison;
                }
            }
            if (dataC.Comparison < 1) {
                dataC.WebAddress.add(sample.getUrlAsString());
            }
        } else {
            dataC.WebAddress.add(sample.getUrlAsString());
        }

        total.setLatency(total.getLatency() + sample.getLatency());
        total.setResponseTime(total.getResponseTime() + sample.getTime());
        total.setConnectTime(total.getConnectTime() + sample.getConnectTime());
        
        log.warn("URL ------ : " + sample.getURL());
        
        total.Comparison = 0;
        if (total.WebAddress.size() != 0) {
            for (int position = 0; position < total.WebAddress.size(); position++) {
                if (total.WebAddress.get(position).equals(sample.getUrlAsString())) {
                    ++total.Comparison;
                }
            }
            if (total.Comparison < 1) {
                total.WebAddress.add(sample.getUrlAsString());
            }
        } else {
            total.WebAddress.add(sample.getUrlAsString());
        }

        saveToList(dataC);
    }

    /**
     *  In this method are results saving to collection of type ArrayList
     * @param dataC passes object of type dataCollector
     */
    private void saveToList(dataCollector dataC) {
        if (arrayDataC.size() == 0) {
            arrayDataC.add(0, dataC);
        }

        if (arrayDataC.get(0).getName() == null) {
            arrayDataC.get(0).setName(dataC.getName());
            arrayDataC.get(0).setLoopCount(dataC.getLoopCount());
            arrayDataC.get(0).setMean(dataC.getMean());
            arrayDataC.get(0).setMinDataC(dataC.getMinDataC());
            arrayDataC.get(0).setMaxDataC(dataC.getMaxDataC());

            arrayDataC.get(0).setStDev(dataC.getStDev());
            arrayDataC.get(0).setErrorPercent(dataC.getErrorPercent());
            arrayDataC.get(0).setRate(dataC.getRate());
            arrayDataC.get(0).setReceivedBytes(dataC.getReceivedBytes());
            arrayDataC.get(0).setSentBytes(dataC.getSentBytes());
            arrayDataC.get(0).setAvgBytes(dataC.getAvgBytes());

            arrayDataC.get(0).setLatency(dataC.getLatency());
            arrayDataC.get(0).setUserCount(dataC.getUserCount());
            arrayDataC.get(0).setResponseTime(dataC.getResponseTime());
            arrayDataC.get(0).setConnectTime(dataC.getConnectTime());
            arrayDataC.get(0).WebAddress = dataC.WebAddress;
        }
        
        
        dataC.Comparison = 0;
        int findName = 0;
        for (int i = 0; i < arrayDataC.size(); i++) {
            if (arrayDataC.get(i).getName().equals(dataC.getName())) {

                arrayDataC.get(i).setLatency(dataC.getLatency() + arrayDataC.get(i).getLatency());
                if (arrayDataC.get(i).getUserCount() < dataC.getUserCount()) {
                    arrayDataC.get(i).setUserCount(dataC.getUserCount());
                }
                arrayDataC.get(i).setResponseTime(dataC.getResponseTime() + arrayDataC.get(i).getResponseTime());
                arrayDataC.get(i).setConnectTime(dataC.getConnectTime() + arrayDataC.get(i).getConnectTime());
                int findAddress = 0;
                for (int a = 0; a < arrayDataC.get(i).WebAddress.size(); a++) {
                    for (int b = 0; b < dataC.WebAddress.size(); b++) {
                        if (arrayDataC.get(i).WebAddress.get(a).equals(dataC.WebAddress.get(b))) {
                            findAddress++;
                        }
                    }
                }
                if (findAddress == 0) {
                    for (int p = 0; p < dataC.WebAddress.size(); p++) {
                        arrayDataC.get(i).WebAddress.add(dataC.WebAddress.get(p));
                    }
                }
            } else {
                findName++;
            }
            if (findName == arrayDataC.size()) {
                arrayDataC.add(i++, dataC);
            }
        }
    }

    /**
     * Saving data to collection ArrayList from maps.
     *
     * @param dataMap map including results of calculator for every thread group.
     * @param samplingMap map including results of samplingCalculator for every thread group.
     */
    private void saveToList(Map<String, Calculator> dataMap, Map<String, SamplingStatCalculator> samplingMap) {
    
        for (dataCollector anArrayDataC : arrayDataC) {
            for (int position = 0; position < dataMap.size(); position++) {
                if (dataMap.get(anArrayDataC.getName()).getLabel().equals(anArrayDataC.getName())) {
                	anArrayDataC.setLoopCount(dataMap.get(anArrayDataC.getName()).getCount());
                    anArrayDataC.setMean(dataMap.get(anArrayDataC.getName()).getMean());
                    anArrayDataC.setMinDataC(dataMap.get(anArrayDataC.getName()).getMin());
                    anArrayDataC.setMaxDataC(dataMap.get(anArrayDataC.getName()).getMax());
                    anArrayDataC.setStDev(dataMap.get(anArrayDataC.getName()).getStandardDeviation());
                    anArrayDataC.setErrorPercent(dataMap.get(anArrayDataC.getName()).getErrorPercentage());
                    anArrayDataC.setRate(dataMap.get(anArrayDataC.getName()).getRate());
                    anArrayDataC.setReceivedBytes(dataMap.get(anArrayDataC.getName()).getKBPerSecond());
                    anArrayDataC.setSentBytes(dataMap.get(anArrayDataC.getName()).getSentKBPerSecond());
                    anArrayDataC.setAvgBytes(dataMap.get(anArrayDataC.getName()).getAvgPageBytes());
                    //Passes percentiles ---------------------------------------------------------------------
                    anArrayDataC.setPercentile_90th(samplingMap.get(anArrayDataC.getName()).getPercentPoint(0.90).doubleValue());
                    anArrayDataC.setPercentile_95th(samplingMap.get(anArrayDataC.getName()).getPercentPoint(0.95).doubleValue());
                    anArrayDataC.setPercentile_99th(samplingMap.get(anArrayDataC.getName()).getPercentPoint(0.99).doubleValue());
                    // -----------------------------------------------------------------------------------------
                    //arrayDataC.get(position).setBandwidth(dataC.getBandwidth());
                }
            }
            try {
                anArrayDataC.setLatency(anArrayDataC.getLatency() / anArrayDataC.getLoopCount());
                anArrayDataC.setResponseTime(anArrayDataC.getResponseTime() / anArrayDataC.getLoopCount());
                anArrayDataC.setConnectTime(anArrayDataC.getConnectTime() / anArrayDataC.getLoopCount());
            } catch (Exception e) {
            	log.error(JMeterUtils.getResString("web_generator_dividing_by_zer") + anArrayDataC.getLoopCount() + "\n" + e);
            }
            total.setName(TOTAL_ROW_LABEL);
            total.setLoopCount(dataMap.get(TOTAL_ROW_LABEL).getCount());
            total.setMean(dataMap.get(TOTAL_ROW_LABEL).getMean());
            total.setMinDataC(dataMap.get(TOTAL_ROW_LABEL).getMin());
            total.setMaxDataC(dataMap.get(TOTAL_ROW_LABEL).getMax());
            total.setStDev(dataMap.get(TOTAL_ROW_LABEL).getStandardDeviation());
            total.setErrorPercent(dataMap.get(TOTAL_ROW_LABEL).getErrorPercentage());
            total.setRate(dataMap.get(TOTAL_ROW_LABEL).getRate());
            total.setReceivedBytes(dataMap.get(TOTAL_ROW_LABEL).getKBPerSecond());
            total.setSentBytes(dataMap.get(TOTAL_ROW_LABEL).getSentKBPerSecond());
            total.setAvgBytes(dataMap.get(TOTAL_ROW_LABEL).getAvgPageBytes());
            //Passes percentiles ---------------------------------------------------------------------
            total.setPercentile_90th(samplingMap.get(TOTAL_ROW_LABEL).getPercentPoint(0.90).doubleValue());
            total.setPercentile_95th(samplingMap.get(TOTAL_ROW_LABEL).getPercentPoint(0.95).doubleValue());
            total.setPercentile_99th(samplingMap.get(TOTAL_ROW_LABEL).getPercentPoint(0.99).doubleValue());
            // -----------------------------------------------------------------------------------------
            total.setErrorPercent(total.getErrorPercent() * 100);
            total.setUserCount(JMeterContextService.getTotalThreads());

            try {
                total.setLatency(total.getLatency() / total.getLoopCount());
                total.setResponseTime(total.getResponseTime() / total.getLoopCount());
                total.setConnectTime(total.getConnectTime() / total.getLoopCount());
            } catch (Exception e){
                log.error(JMeterUtils.getResString("web_generator_dividing_by_zer") + anArrayDataC.getLoopCount() + "\n" + e);
            }
        }
    }

    /**
     * Whole collected data are write to JavaScript file in web folder.
     * @param arrayCompleteData collected data from test. Type ArrayList.
     */
    private void editFunctionsJS(ArrayList<dataCollector> arrayCompleteData) {
        saveToList(tableRows, samplingRows); //Here is passes data from calculator adn samplingCalculator
        total.setName(JMeterUtils.getResString("web_generator_row_total"));
        if (arrayCompleteData != null) {
            String pathToDataFile = reportOutputFolder + "/content/js/data.js";
            pathToDataFile = FilenameUtils.separatorsToSystem(pathToDataFile);
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(pathToDataFile, true))) {
                bw.newLine();
                bw.write("var threadGroupsCount = " + Integer.toString(arrayCompleteData.size()) + ";");
                bw.newLine();
                bw.write("var dataResult = [");
                for (dataCollector anArrayCompleteData1 : arrayCompleteData) {
                    bw.write("[");
                    bw.write('\"' + anArrayCompleteData1.getName() + '\"' + ",");
                    bw.write(anArrayCompleteData1.getUserCount() + ",");
                    bw.write(anArrayCompleteData1.getLoopCount() + ",");
                    bw.write(anArrayCompleteData1.getMean() + ",");
                    bw.write(anArrayCompleteData1.getMinDataC() + ",");
                    bw.write(anArrayCompleteData1.getMaxDataC() + ",");
                    bw.write(anArrayCompleteData1.getStDev() + ",");
                    bw.write(anArrayCompleteData1.getErrorPercent() + ",");
                    bw.write(anArrayCompleteData1.getRate() + ",");
                    bw.write(anArrayCompleteData1.getReceivedBytes() + ",");
                    bw.write(anArrayCompleteData1.getSentBytes() + ",");
                    bw.write(anArrayCompleteData1.getAvgBytes() + ",");
                    bw.write(anArrayCompleteData1.getLatency() + ",");
                    bw.write(anArrayCompleteData1.getConnectTime() + ",");
                    bw.write(anArrayCompleteData1.getResponseTime() + ",");
                    bw.write("[");
                    for (int i = 0; i < anArrayCompleteData1.WebAddress.size(); i++) {
                        bw.write('\"' + anArrayCompleteData1.WebAddress.get(i) + '\"');
                        if (i < anArrayCompleteData1.WebAddress.size() - 1) {
                            bw.write(',');
                        }
                    }
                    bw.write("]]");
                    bw.write(',');
                }
                bw.write('[');
                bw.write('\"' + total.getName() + '\"' + ",");
                bw.write(total.getUserCount() + ",");
                bw.write(total.getLoopCount() + ",");
                bw.write(total.getMean() + ",");
                bw.write(total.getMinDataC() + ",");
                bw.write(total.getMaxDataC() + ",");
                bw.write(total.getStDev() + ",");
                bw.write(total.getErrorPercent() + ",");
                bw.write(total.getRate() + ",");
                bw.write(total.getReceivedBytes() + ",");
                bw.write(total.getSentBytes() + ",");
                bw.write(total.getAvgBytes() + ",");
                bw.write(total.getLatency() + ",");
                bw.write(total.getConnectTime() + ",");
                bw.write(total.getResponseTime() + ",");
                bw.write("[");
                for(int i = 0; i < total.WebAddress.size(); i++){
                    bw.write('\"' + total.WebAddress.get(i) + '\"');
                    if(i < total.WebAddress.size()-1){
                        bw.write(',');
                    }
                }
                bw.write("]]];");

                bw.newLine();

                bw.write("var percentile = [");
                for (dataCollector anArrayCompleteData : arrayCompleteData) {
                    bw.write("[");
                    bw.write('\"' + anArrayCompleteData.getName() + '\"' + ",");
                    bw.write(anArrayCompleteData.getPercentile_90th() + ",");
                    bw.write(anArrayCompleteData.getPercentile_95th() + ",");
                    bw.write(anArrayCompleteData.getPercentile_99th() + ",");
                    bw.write("],");
                }
                bw.write("[");
                bw.write('\"' + total.getName() + '\"' + ",");
                bw.write(total.getPercentile_90th() + ",");
                bw.write(total.getPercentile_95th() + ",");
                bw.write(Double.toString(total.getPercentile_99th()));
                bw.write("]];");
                bw.flush();
            } catch (Exception e) {
                log.error(JMeterUtils.getResString("web_generator_log_error_writing_js") + " " + reportOutputFolder + "\\content\\js\\data.js");
            }
        }
    }

    /**
     * DataCollector class is for saving actual data during testing.
     */
    private class dataCollector {
        private String Name = "";
        private int UserCount = 0;
        private double Mean;
        private long MinDataC;
        private long MaxDataC;
        private double StDev;
        private double Rate;
        private double AvgBytes;
        private double ErrorPercent;
        private float Latency;
        private float ResponseTime;
        private int LoopCount;
        private int Comparison;
        private double ReceivedBytes;
        private double SentBytes;
        private long ConnectTime;
        private double percentile_90th;
        private double percentile_95th;
        private double percentile_99th;

        private ArrayList<String> WebAddress = new ArrayList<>();

        private String getName() {
            return Name;
        }

        private void setName(String name) {
            Name = name;
        }

        private int getUserCount() {
            return UserCount;
        }

        private void setUserCount(int userCount) {
            UserCount = userCount;
        }

        private float getLatency() {
            return Latency;
        }

        private void setLatency(float Latency) {
            this.Latency = Latency;
        }

        private float getResponseTime() {
            return ResponseTime;
        }

        private void setResponseTime(float ResponseTime) {
            this.ResponseTime = ResponseTime;
        }

        private int getLoopCount() {
            return LoopCount;
        }

        private void setLoopCount(int loopCount) {
            LoopCount = loopCount;
        }

        private double getReceivedBytes() {
            return ReceivedBytes;
        }

        private void setReceivedBytes(double receivedBytes) {
            ReceivedBytes = receivedBytes;
        }

        private double getSentBytes() {
            return SentBytes;
        }

        private void setSentBytes(double sentBytes) {
            SentBytes = sentBytes;
        }

        private long getConnectTime() {
            return ConnectTime;
        }

        private void setConnectTime(long connectTime) {
            ConnectTime = connectTime;
        }

        private long getMinDataC() {
            return MinDataC;
        }

        private void setMinDataC(long minDataC) {
            MinDataC = minDataC;
        }

        private long getMaxDataC() {
            return MaxDataC;
        }

        private void setMaxDataC(long maxDataC) {
            MaxDataC = maxDataC;
        }

        private double getStDev() {
            return StDev;
        }

        private void setStDev(double stDev) {
            StDev = stDev;
        }

        private double getAvgBytes() {
            return AvgBytes;
        }

        private void setAvgBytes(double avgBytes) {
            AvgBytes = avgBytes;
        }

        private double getRate() {
            return Rate;
        }

        private void setRate(double rate) {
            Rate = rate;
        }

        private double getErrorPercent() {
            return ErrorPercent;
        }

        private void setErrorPercent(double errorPercent) {
            ErrorPercent = errorPercent;
        }

        private double getMean() {
            return Mean;
        }

        private void setMean(double mean) {
            Mean = mean;
        }

        private double getPercentile_90th() {
            return percentile_90th;
        }

        private void setPercentile_90th(double percentile_90th) {
            this.percentile_90th = percentile_90th;
        }

        private double getPercentile_95th() {
            return percentile_95th;
        }

        private void setPercentile_95th(double percentile_95th) {
            this.percentile_95th = percentile_95th;
        }

        private double getPercentile_99th() {
            return percentile_99th;
        }

        private void setPercentile_99th(double percentile_99th) {
            this.percentile_99th = percentile_99th;
        }
    }
}