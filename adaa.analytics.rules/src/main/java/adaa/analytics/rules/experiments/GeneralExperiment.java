/*******************************************************************************
 * Copyright (C) 2019 RuleKit Development Team
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU Affero General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 *  Affero General Public License for more details.
 *  
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see http://www.gnu.org/licenses/.
 ******************************************************************************/
package adaa.analytics.rules.experiments;

import adaa.analytics.rules.logic.representation.Logger;
import adaa.analytics.rules.logic.representation.SurvivalRule;
import adaa.analytics.rules.operator.RDataExampleSource;
import adaa.analytics.rules.utils.RapidMiner5;

import com.rapidminer.example.Attributes;
import com.rapidminer.operator.*;
import com.rapidminer.operator.performance.PerformanceVector;
import com.rapidminer.operator.preprocessing.filter.ChangeAttributeRole;
import com.rapidminer.tools.OperatorService;
import com.rapidminer5.operator.io.ArffExampleSource;

import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.IOException;
import java.security.InvalidParameterException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class GeneralExperiment extends ExperimentBase {

    protected File file;

    protected Operator trainOperator;

    protected Operator testOperator;

    protected Extension ext;
    
	protected List<Map<String,Object>> paramsSets;


    public GeneralExperiment(
            File file,
            SynchronizedReport qualityReport,
            SynchronizedReport modelReport,
            String labelAttribute,
            Map<String, String> options,
            Map<String, Object> params) {

        super(qualityReport, modelReport);
        try {
            this.file = file;

            this.paramsSets = paramsSets;
           
            //Creating Operators
            //Different operator for different data file formats
            ext = getExtension(file);
            if (ext == Extension.ARFF) {
                trainOperator = RapidMiner5.createOperator(ArffExampleSource.class);
                testOperator = RapidMiner5.createOperator(ArffExampleSource.class);
            } else if (ext == Extension.RData) {
                trainOperator = OperatorService.createOperator(RDataExampleSource.class);
                testOperator = OperatorService.createOperator(RDataExampleSource.class);
            } else {
                throw new IOException(file.getName() + " not supported data format.");
            }
            ChangeAttributeRole trainRoleSetter = (ChangeAttributeRole) OperatorService.createOperator(ChangeAttributeRole.class);
            ChangeAttributeRole testRoleSetter = (ChangeAttributeRole) OperatorService.createOperator(ChangeAttributeRole.class);
            ModelApplier applier = (ModelApplier) OperatorService.createOperator(ModelApplier.class);


            // configure main process
            process = new com.rapidminer.Process();
            process.getRootOperator().getSubprocess(0).addOperator(trainOperator);
            process.getRootOperator().getSubprocess(0).addOperator(testOperator);
            process.getRootOperator().getSubprocess(0).addOperator(trainRoleSetter);
            process.getRootOperator().getSubprocess(0).addOperator(testRoleSetter);
            process.getRootOperator().getSubprocess(0).addOperator(ruleGenerator);
            process.getRootOperator().getSubprocess(0).addOperator(applier);
            process.getRootOperator().getSubprocess(0).addOperator(validationEvaluator);

            trainOperator.getOutputPorts().getPortByName("output").connectTo(trainRoleSetter.getInputPorts().getPortByName("example set input"));
            trainRoleSetter.getOutputPorts().getPortByName("example set output").connectTo(ruleGenerator.getInputPorts().getPortByName("training set"));

            testOperator.getOutputPorts().getPortByName("output").connectTo(testRoleSetter.getInputPorts().getPortByName("example set input"));
            testRoleSetter.getOutputPorts().getPortByName("example set output").connectTo(applier.getInputPorts().getPortByName("unlabelled data"));

            ruleGenerator.getOutputPorts().getPortByName("model").connectTo(applier.getInputPorts().getPortByName("model"));

            applier.getOutputPorts().getPortByName("labelled data").connectTo(
                    validationEvaluator.getInputPorts().getPortByName("labelled data"));

            // pass estimated performance to 
            ruleGenerator.getOutputPorts().getPortByName("estimated performance").connectTo(
                    validationEvaluator.getInputPorts().getPortByName("performance"));

            validationEvaluator.getOutputPorts().getPortByName("performance").connectTo(
                    process.getRootOperator().getSubprocess(0).getInnerSinks().getPortByIndex(0));
            applier.getOutputPorts().getPortByName("model").connectTo(
                    process.getRootOperator().getSubprocess(0).getInnerSinks().getPortByIndex(1));


            // configure role setter
            trainRoleSetter.setParameter(trainRoleSetter.PARAMETER_NAME, labelAttribute);
            trainRoleSetter.setParameter(trainRoleSetter.PARAMETER_TARGET_ROLE, Attributes.LABEL_NAME);

            testRoleSetter.setParameter(testRoleSetter.PARAMETER_NAME, labelAttribute);
            testRoleSetter.setParameter(testRoleSetter.PARAMETER_TARGET_ROLE, Attributes.LABEL_NAME);

            // survival dataset - set proper role
            List<String[]> roles = new ArrayList<String[]>();

            if (options.containsKey(SurvivalRule.SURVIVAL_TIME_ROLE)) {
                roles.add(new String[]{options.get(SurvivalRule.SURVIVAL_TIME_ROLE), SurvivalRule.SURVIVAL_TIME_ROLE});
            }

            if (options.containsKey(Attributes.WEIGHT_NAME)) {
                roles.add(new String[]{options.get(Attributes.WEIGHT_NAME), Attributes.WEIGHT_NAME});
            }

            if (roles.size() > 0) {
                trainRoleSetter.setListParameter(trainRoleSetter.PARAMETER_CHANGE_ATTRIBUTES, roles);
                testRoleSetter.setListParameter(testRoleSetter.PARAMETER_CHANGE_ATTRIBUTES, roles);
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {

        try {

            Map<String, Object> params = paramsSets.get(0);

            for (String key : params.keySet()) {
                Object o = params.get(key);

                if (o instanceof String) {
                    ruleGenerator.setParameter(key, (String) o);
                } else if (o instanceof List) {
                    ruleGenerator.setListParameter(key, (List<String[]>) o);
                } else {
                    throw new InvalidParameterException();
                }
            }
            DateFormat dateFormat = new SimpleDateFormat("yyyy.MM.dd_HH.mm.ss");
            if (file.isDirectory()) {
                System.out.println(file.getAbsolutePath());
                File testFile = new File(file.getAbsolutePath() + "/test");
                File trainFile = new File(file.getAbsolutePath() + "/train");
                if (testFile.exists() && trainFile.exists()) { //lista danych testowych i treningowych

                    for (File child:trainFile.listFiles()) {

                        File test = new File(child.getAbsolutePath().replace("train", "test"));

                        System.out.println(child);
                        System.out.println(test);
                        runExperiment(child,test,dateFormat);
                    }
                } else {  //lista danych tylko treningowe
                    File[] files = file.listFiles();
                    for (File child : files) {
                        runExperiment(child, child, dateFormat);
                    }
                }
            } else { //Pojedynczy plik z danymi
                runExperiment(file, file, dateFormat);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runExperiment(File trainFile, File testFile, DateFormat dateFormat) throws OperatorException, IOException {

        Date begin = new Date();
        String dateString = dateFormat.format(begin);

        Logger.log("started!\n", Level.FINE);
        if (ext == Extension.ARFF) {
            trainOperator.setParameter(ArffExampleSource.PARAMETER_DATA_FILE, trainFile.getAbsolutePath());
            testOperator.setParameter(ArffExampleSource.PARAMETER_DATA_FILE, testFile.getAbsolutePath());
        } else {
            trainOperator.setParameter(RDataExampleSource.PARAMETER_DATA_FILE, trainFile.getAbsolutePath());
            testOperator.setParameter(RDataExampleSource.PARAMETER_DATA_FILE, testFile.getAbsolutePath());
        }
        long t1 = System.nanoTime();
        IOContainer out = process.run();
        IOObject[] objs = out.getIOObjects();
        long t2 = System.nanoTime();
        double elapsedSec = (double) (t2 - t1) / 1e9;

        PerformanceVector performance = (PerformanceVector) objs[0];

        if (modelReport != null) {
            StringBuilder sb = new StringBuilder();
            sb.append(StringUtils.repeat("=", 80));
            sb.append("\n");
            sb.append(testFile);
            sb.append("\n\n");
            Model model = (Model) objs[1];
            sb.append(model.toString());
            sb.append("\n");
            // add performance
            for (String name : performance.getCriteriaNames()) {
                double avg = performance.getCriterion(name).getAverage();
                sb.append(name + ": " + avg + "\n");
            }

            sb.append("\n\n");
            modelReport.append(sb.toString());
        }
        String[] columns = performance.getCriteriaNames();
        Logger.log(performance + "\n", Level.FINE);
        // generate headers
        String performanceHeader = "Dataset, time started, elapsed[s], ";
        String row = testFile + "," + dateString + "," + elapsedSec + ",";
        for (String name : columns) {
            performanceHeader += name + ",";
        }
        for (String name : performance.getCriteriaNames()) {
            double avg = performance.getCriterion(name).getAverage();
            row += avg + ", ";
        }
        qualityReport.add(new String[]{ruleGenerator.toString(), performanceHeader}, row);
    }


    private Extension getExtension(File fileOrFolder) throws IOException {
        if (fileOrFolder.isDirectory()) {
            File file = getFile(fileOrFolder);
            return checkFile(file);
        } else {
            return checkFile(fileOrFolder);
        }
    }

    private File getFile(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) {
                throw new IOException();
            }
            for (File childFile : files) {
                return getFile(childFile);
            }
            return null;
        } else {
            return file;
        }
    }

    private Extension checkFile(File file) {
        String name = file.getName();
        int index = name.lastIndexOf(".");
        if (index == -1) {
            return Extension.NoExtension; // empty extension
        }
        String extension = name.substring(index);
        if (extension.equalsIgnoreCase(".arff"))
            return Extension.ARFF;
        if (extension.equalsIgnoreCase(".rdata"))
            return Extension.RData;
        return Extension.NoExtension;
    }

    private enum Extension {
        ARFF,
        RData,
        NoExtension
    }
}
