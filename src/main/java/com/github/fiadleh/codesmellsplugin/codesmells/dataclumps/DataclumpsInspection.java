package com.github.fiadleh.codesmellsplugin.codesmells.dataclumps;

import com.github.fiadleh.codesmellsplugin.services.CodesmellTimer;
import com.github.fiadleh.codesmellsplugin.services.PsiGroup;
import com.github.fiadleh.codesmellsplugin.util.CacheManager;
import com.github.fiadleh.codesmellsplugin.util.PsiUtils;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

/**
 * Main inspection class for data clumps. Responsible for starting checks and reporting found instances.
 *
 * @author Firas Adleh
 */
public class DataclumpsInspection extends LocalInspectionTool {

    /**
     * Text to be displayed in problem description
     */
    public static final String QUICK_FIX_NAME = "Extract dataclumps variables to a new class.";

    /**
     * Group name in inspections configuration window
     */
    private static final String GROUP_DISPLAY_NAME = "Code Smells";

    /**
     * Inspection name in inspections configuration window
     */
    private static final String CODE_SMELL_DISPLAY_NAME = "Data Clumps";

    /**
     * The lowest possible minimum value, at least 2 smelly variables
     */
    private static final int MIN_VARIABLES_COUNT_DEFAULT = 2;

    /**
     * Allow checking methods in the same class, default = true
     */
    private static boolean includeMethodsInSameCLass = true;

    /**
     * Force hierarchy independence for data clumps fields instances, default = false
     */
    private static boolean checkHierarchyInFieldsInstances = false;

    /**
     * Force hierarchy independence for data clumps parameters instances, default = false
     */
    private static boolean checkHierarchyInParametersInstances = false;

    /**
     * The current minimum value, it can be changed in inspection preferences
     */
    private static int minParametersCount = 3;

    /**
     * The current minimum value, it can be changed in inspection preferences
     */
    private static int minFieldsCount = 3;


    /**
     * A flag for activating writing reported data clumps instances to an external XML file
     */
    private static final boolean DEBUG_XML = false;

    /**
     * An external XML file containing reported data clumps instances
     */
    private static FileWriter outputFile = null;

    /**
     * An identifier used to mark the lines made by this class in log
     */
    private static final String LOGGER_NAME = "DataclumpsInspection";

    /**
     * The refactoring class for data clumps parameters instances
     */
    private final ParameterDataClumpFix parameterDataclumpQuickFix = new ParameterDataClumpFix();

    /**
     * The refactoring class for data clumps fields instances
     */
    private final FieldsDataClumpFix fieldsDataclumpQuickFix = new FieldsDataClumpFix();


    /**
     * A timer to measure the required time for searching
     */
    private final CodesmellTimer detectionTimer = new CodesmellTimer("Dataclumps detection");


    /**
     * adds another report problem to be detected by automatic inspection testing
     * this value should be always false and turned true only by inspection testing
     */
    private static boolean isTestingReports = false;

    /**
     * Update the includeMethodsInSameCLass value
     * called from the inspection configuration page
     *
     * @param includeMethodsInSameCLass new boolean value
     */
    public static void setIncludeMethodsInSameCLass(boolean includeMethodsInSameCLass) {
        DataclumpsInspection.includeMethodsInSameCLass = includeMethodsInSameCLass;
    }

    /**
     * Update the checkHierarchyInFieldsInstances value
     * called from the inspection configuration page
     *
     * @param checkHierarchyInFieldsInstances
     */
    public static void setCheckHierarchyInFieldsInstances(boolean checkHierarchyInFieldsInstances) {
        DataclumpsInspection.checkHierarchyInFieldsInstances = checkHierarchyInFieldsInstances;
    }

    /**
     * Update the checkHierarchyInParametersInstances value
     * called from the inspection configuration page
     *
     * @param checkHierarchyInParametersInstances
     */
    public static void setCheckHierarchyInParametersInstances(boolean checkHierarchyInParametersInstances) {
        DataclumpsInspection.checkHierarchyInParametersInstances = checkHierarchyInParametersInstances;
    }

    /**
     * Update the minParametersCount value
     * called from the inspection configuration page
     *
     * @param minParametersCount
     */
    public static void setMinParametersCount(int minParametersCount) {
        DataclumpsInspection.minParametersCount = minParametersCount;
    }

    /**
     * Update the minFieldsCount value
     * called from the inspection configuration page
     *
     * @param minFieldsCount
     */
    public static void setMinFieldsCount(int minFieldsCount) {
        DataclumpsInspection.minFieldsCount = minFieldsCount;
    }



    /**
     * this is called only when testing inspections to catch all reported problems
     */
    public static void activateTesting() {
        isTestingReports = true;
    }


    /**
     * This is called in inspection configuration in settings to allow the user to
     * change the required thresholds to detect data clumps instances.
     * All required swing elements are created here and added to a JPanel element.
     *
     * @return panel that have all inspection configuration elements.
     */
    @Override
    public JComponent createOptionsPanel() {
        JPanel newPanel = new JPanel(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.insets = JBUI.insets(10);

        JLabel labelMinParametersCount = new JLabel("Min parameters count: ");
        JLabel labelMinFieldsCount = new JLabel("Min fields count: ");
        final JTextField minParametersCountTF = new JTextField(Integer.toString(minParametersCount));
        final JTextField minFieldsCountTF = new JTextField(Integer.toString(minFieldsCount));

        final JCheckBox includeMethodsInSameCLassCB = new JCheckBox("Include methods in same cLass", includeMethodsInSameCLass);
        final JCheckBox checkHierarchyInFieldsInstancesCB = new JCheckBox("Classes in fields data clump instances must have different hierarchy", checkHierarchyInFieldsInstances);
        final JCheckBox checkHierarchyInParametersInstancesCB = new JCheckBox("Classes in parameters data clump instances must have different hierarchy", checkHierarchyInParametersInstances);

        minParametersCountTF.getDocument().addDocumentListener(new DocumentAdapter() {
            public void textChanged(@NotNull DocumentEvent event) {
                if (!minParametersCountTF.getText().equals("")) {
                    setMinParametersCount(Integer.parseInt(minParametersCountTF.getText()));
                }
                if (minParametersCount < MIN_VARIABLES_COUNT_DEFAULT) {
                    setMinParametersCount(MIN_VARIABLES_COUNT_DEFAULT);
                }
            }
        });

        minFieldsCountTF.getDocument().addDocumentListener(new DocumentAdapter() {
            public void textChanged(@NotNull DocumentEvent event) {
                if (!minFieldsCountTF.getText().equals("")) {
                    setMinFieldsCount(Integer.parseInt(minFieldsCountTF.getText()));
                }
                if (minFieldsCount < MIN_VARIABLES_COUNT_DEFAULT) {
                    setMinFieldsCount(MIN_VARIABLES_COUNT_DEFAULT);
                }
            }
        });

        includeMethodsInSameCLassCB.addItemListener(e -> {
            setIncludeMethodsInSameCLass(false);
            if (e.getStateChange() == ItemEvent.SELECTED) {
                setIncludeMethodsInSameCLass(true);
            }
        });

        checkHierarchyInFieldsInstancesCB.addItemListener(e -> {
            setCheckHierarchyInFieldsInstances(false);
            if (e.getStateChange() == ItemEvent.SELECTED) {
                setCheckHierarchyInFieldsInstances(true);
            }
        });

        checkHierarchyInParametersInstancesCB.addItemListener(e -> {
            setCheckHierarchyInParametersInstances(false);
            if (e.getStateChange() == ItemEvent.SELECTED) {
                setCheckHierarchyInParametersInstances(true);
            }
        });

        // add components to the panel
        constraints.gridx = 0;
        constraints.gridy = 0;
        newPanel.add(labelMinParametersCount, constraints);

        constraints.gridx = 1;
        newPanel.add(minParametersCountTF, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        newPanel.add(labelMinFieldsCount, constraints);

        constraints.gridx = 1;
        newPanel.add(minFieldsCountTF, constraints);


        constraints.gridx = 0;
        constraints.gridy = 2;
        constraints.gridwidth = 2;
        newPanel.add(includeMethodsInSameCLassCB, constraints);

        constraints.gridx = 0;
        constraints.gridy = 3;
        constraints.gridwidth = 2;

        newPanel.add(checkHierarchyInFieldsInstancesCB, constraints);

        constraints.gridx = 0;
        constraints.gridy = 4;
        constraints.gridwidth = 2;
        newPanel.add(checkHierarchyInParametersInstancesCB, constraints);

        return newPanel;
    }

    /**
     * This is called automatically when inspection starts.
     *
     * @param session
     * @param isOnTheFly
     */
    @Override
    public void inspectionStarted(@NotNull LocalInspectionToolSession session, boolean isOnTheFly) {
        if (PsiUtils.isDebugMode()) {
            detectionTimer.startTimer();
        }

        if (DEBUG_XML) {
            createXMLFile();
        }
    }

    /**
     * Create an XML file to save the detected instances into it for debugging and comparing.
     */
    public static void createXMLFile() {
        if (outputFile == null) {
            try {
                outputFile = new FileWriter("D:\\Uni\\BA\\Code Smell\\python\\results.xml", true);
            } catch (IOException e) {
                PsiUtils.log(LOGGER_NAME, "XML create error.");
            }
        }
    }

    /**
     * This is called automatically when inspection stops.
     *
     * @param session
     * @param problemsHolder
     */
    @Override
    public void inspectionFinished(@NotNull LocalInspectionToolSession session, @NotNull ProblemsHolder problemsHolder) {
        if (PsiUtils.isDebugMode()) {
            detectionTimer.stopTimer();
            detectionTimer.printMessage();
        }

        if (DEBUG_XML) {
            try {
                outputFile.flush();
            } catch (IOException e) {
                PsiUtils.log(LOGGER_NAME, "XML flush error.");
            }
        }
    }

    /**
     * This is called automatically when the user edits code or lunches a custom inspection scan.
     * An instance of JavaElementVisitor is created here to visit the different code parts.
     * There is a problem holder that receives detected data clumps instances as problems.
     *
     * @param holder
     * @param isOnTheFly
     * @return
     */
    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {


            @Override
            public void visitClass(PsiClass currentClass) {
                if (currentClass.getQualifiedName() != null && !CacheManager.getAllClassesQualifiedNames().contains(currentClass.getQualifiedName())) {
                    CacheManager.addClassToCache(currentClass);
                }

                // check fields in this class for data clumps
                checkFieldsDataclumps(currentClass, holder);

                detectionTimer.setClassName(currentClass.getName() + ", TextLength = " + currentClass.getTextLength() + ", Methods = " +
                        currentClass.getMethods().length + " ");
            }

            @Override
            public void visitReferenceExpression(PsiReferenceExpression psiReferenceExpression) {
                //To avoid duplicate visitations
            }

            @Override
            public void visitMethod(PsiMethod method) {

                PsiParameterList list = method.getParameterList();
                PsiClass currentClass = method.getContainingClass();

                // exclude methods inherited from parent class
                if (method.hasAnnotation("java.lang.Override")) {
                    return;
                }

                if (list.getParametersCount() >= minParametersCount &&
                        currentClass != null && !method.getName().equals(currentClass.getName()) // avoid constructors
                ) {
                    checkParameterDataclumps(list, holder);

                }

            }
        };
    }

    /**
     * Searches the given class fields for data clumps and return a list of classes having the same group of fields
     * also reports the data clumps if a problem holder is passed
     *
     * @param currentClass class to be scanned
     * @param holder       if not null, report all found data clumps as a problems
     * @return a list of classes having the same group of fields as the given class
     */
    private ArrayList<PsiClass> checkFieldsDataclumps(PsiClass currentClass, ProblemsHolder holder) {
        ArrayList<PsiClass> dataclumpClasses = new ArrayList<>();
        String currentClassQualifiedName = currentClass.getQualifiedName();
        if (currentClassQualifiedName == null) {
            return dataclumpClasses;
        }
        List<PsiClass> allClasses = new ArrayList<>(CacheManager.getAllClasses(currentClass.getProject()));
        for (PsiClass c : allClasses) {
            try {
                if (
                        c.isValid() &&
                                PsiUtils.countCommonFields(currentClass, c) >= minFieldsCount &&
                                !currentClassQualifiedName.equals(c.getQualifiedName()) &&
                                (!checkHierarchyInFieldsInstances || !PsiUtils.hasCommonHierarchy(currentClass, c))
                ) {

                    List<PsiField> dataclumpFields = PsiUtils.getCommonFields(currentClass, c);
                    dataclumpClasses.add(c);


                    StringJoiner allFieldsText = new StringJoiner(" , ");
                    dataclumpFields.forEach(item -> allFieldsText.add(item.toString()));


                    registerProblem(holder,
                            currentClass.getContainingFile(),
                            new TextRange(dataclumpFields.get(0).getTextOffset() - dataclumpFields.get(0).getName().length() + 1,
                                    dataclumpFields.get(dataclumpFields.size() - 1).getTextOffset() + dataclumpFields.get(dataclumpFields.size() - 1).getName().length()),
                            PsiUtils.countCommonFields(currentClass, c) +
                                    " Fields in file: " + c.getContainingFile().getVirtualFile().getUrl() +
                                    " in class: " + currentClass.getName() +
                                    ",Fields: " + allFieldsText);


                    writeToXML("<Info type=\"Fields\" fields=\"" + allFieldsText + "\" location=\"" +
                            currentClass.getContainingFile().getVirtualFile().getUrl() + "  &amp; " +
                            c.getContainingFile().getVirtualFile().getUrl() +
                            "\" ></Info>\n");

                }
            } catch (PsiInvalidElementAccessException e) {
                // ignore deleted classes
            }
        }

        return dataclumpClasses;
    }

    /**
     * Reports the found data clumps instances to the problem holder
     *
     * @param holder
     * @param psiElement
     * @param rangeInElement
     * @param descriptionTemplate
     */
    private void registerProblem(ProblemsHolder holder,
                                 @NotNull PsiElement psiElement,
                                 @Nullable TextRange rangeInElement,
                                 @NotNull @InspectionMessage String descriptionTemplate) {

        if (holder != null) {
            // report either fields or parameters instance
            if (rangeInElement != null) {
                holder.registerProblem(psiElement,
                        rangeInElement,
                        descriptionTemplate,
                        fieldsDataclumpQuickFix);
            } else {
                holder.registerProblem(psiElement,
                        null,
                        descriptionTemplate,
                        parameterDataclumpQuickFix);
            }
        }

        registerProblemForTesting(holder, psiElement.getContainingFile());

    }

    /**
     * Searches for parameters data clumps instances related to the given parameter list
     * returns the list of detected parameter lists containing data clumps
     *
     * @param currentList parameter list to be checked
     * @param holder      if null then don't report data clumps
     * @return a list of detected parameter lists containing data clumps
     */
    private List<PsiParameterList> checkParameterDataclumps(PsiParameterList currentList, ProblemsHolder holder) {

        List<PsiParameterList> dataclumpParametherLists = new ArrayList<>();
        PsiMethod currentMethod = ((PsiMethod) currentList.getParent());
        PsiClass currentClass = currentMethod.getContainingClass();

        // avoid checking inherited methods
        if (!checkHierarchyInParametersInstances && currentMethod.findSuperMethods().length != 0) {
            return dataclumpParametherLists;
        }


        List<PsiClass> allClasses = new ArrayList<>(CacheManager.getAllClasses(currentClass.getProject()));
        for (PsiClass c : allClasses) {


            boolean isSameClass = isSameClass(currentClass, c);

            if (!c.isValid()) {
                continue;
            }

            for (PsiMethod fileMethod : c.getMethods()) {
                if (
                        checkMethod(currentList, currentMethod, isSameClass, fileMethod)
                ) {

                    dataclumpParametherLists.add(fileMethod.getParameterList());


                    registerProblem(holder,
                            currentList,
                            null,
                            "" + PsiUtils.countCommonParameters(currentList, fileMethod.getParameterList(), false) + " Parameters in " +
                                    " file: " + fileMethod.getContainingFile().getVirtualFile().getUrl() +
                                    " in class : " + Objects.requireNonNull(fileMethod.getContainingClass()).getQualifiedName() +
                                    ", method: " + fileMethod.getName()
                    );


                    writeToXML("<Info type=\"Parameters\" method1=\"" + currentMethod.getName() + "\"  method2=\"" + fileMethod.getName() + "\" location=\"" + currentMethod.getContainingFile().getVirtualFile().getUrl() + "  &amp; " + fileMethod.getContainingFile().getVirtualFile().getUrl() + "\" ></Info>\n");

                }
            }

            checkAlreadyExtractedClass(currentList, holder, dataclumpParametherLists, c);
        }

        return dataclumpParametherLists;
    }

    /**
     * Checks if the given method and parameter list have a data clumps instanc
     *
     * @param currentList
     * @param currentMethod
     * @param isSameClass           currentList and currentMethod are in the same class
     * @param fileMethod
     * @return
     */
    private boolean checkMethod(PsiParameterList currentList, PsiMethod currentMethod, boolean isSameClass, PsiMethod fileMethod) {
        return (checkHierarchyInParametersInstances || fileMethod.findSuperMethods().length == 0) && // avoid inherited methods if checkHierarchyInParametersInstances is off
                !(fileMethod.getName().equals(currentMethod.getName()) && isSameClass) && // avoid overloaded methods
                !fileMethod.hasAnnotation("java.lang.Override") &&  // avoid overrided methods
                !fileMethod.getName().equals(fileMethod.getContainingClass().getName()) && // avoid constructors
                PsiUtils.countCommonParameters(currentList, fileMethod.getParameterList(), false) >= minParametersCount
                && (
                (!checkHierarchyInParametersInstances && !isSameClass) ||
                        (includeMethodsInSameCLass && isSameClass) ||
                        !PsiUtils.hasCommonHierarchy(currentMethod.getContainingClass(), fileMethod.getContainingClass())
        );
    }

    /**
     * check if the given class is already an extracted class with the same duplicated parameters found in currentList
     *
     * @param currentList
     * @param holder
     * @param dataclumpParametherLists
     * @param c
     */
    private void checkAlreadyExtractedClass(PsiParameterList currentList, ProblemsHolder holder, List<PsiParameterList> dataclumpParametherLists, PsiClass c) {
        // check if there is already an extracted class with the same duplicated parameters
        if (PsiUtils.countCommonFields(c, currentList) >= minParametersCount && c.getConstructors().length > 0 && c.isValid()) {

            // add the extracted class constructor as another connection to be used later to find common fields
            dataclumpParametherLists.add(c.getConstructors()[0].getParameterList());
            PsiUtils.log(LOGGER_NAME, "dataclumpParametherLists: " + dataclumpParametherLists.size());

            registerProblem(holder,
                    currentList,
                    null,
                    "" + PsiUtils.countCommonFields(c, currentList) + " Fields in " +
                            " file: " + Objects.requireNonNull(currentList).getContainingFile().getVirtualFile().getUrl() +
                            " in already extracted class : " + Objects.requireNonNull(((PsiMethod) currentList.getParent()).getContainingClass()).getQualifiedName()
            );

        }
    }

    /**
     * Used in the plugin's modal testing
     *
     * @param holder
     * @param currentElement
     */
    private void registerProblemForTesting(ProblemsHolder holder, PsiElement currentElement) {
        if (holder != null && isTestingReports) {
            holder.registerProblem(currentElement, " Inspections testing", parameterDataclumpQuickFix);
        }
    }

    /**
     * checks if two classes are the same according to their qualified names
     *
     * @param class1 first class
     * @param class2 second class to compare with
     * @return true or false
     */
    public boolean isSameClass(PsiClass class1, PsiClass class2) {
        if (class1 != null && class2 != null && class1.isValid() && class2.isValid() && class1.getQualifiedName() != null && class2.getQualifiedName() != null) {
            return class1.getQualifiedName().equals(class2.getQualifiedName());
        }
        return false;
    }

    /**
     * Output data to the XML file
     *
     * @param text
     */
    public static void writeToXML(String text) {
        if (DEBUG_XML) {
            try {
                outputFile.write(text);
            } catch (IOException e) {
                PsiUtils.log(LOGGER_NAME, "XML write: An error occurred.");
            }
        }
    }

    @Override
    @NotNull
    public String getGroupDisplayName() {
        return GROUP_DISPLAY_NAME;
    }

    @Override
    @NotNull
    public String getDisplayName() {
        return CODE_SMELL_DISPLAY_NAME;
    }


    /**
     * Receives the detected data clumps parameters instance and redirect the refactoring to DataclassRefactoringService
     */
    private class ParameterDataClumpFix implements LocalQuickFix {
        /**
         * Returns a name for this quick fix class
         * required by testing using LightJavaCodeInsightFixtureTestCase
         *
         * @return Quick fix name.
         */
        @NotNull
        @Override
        public String getName() {
            return QUICK_FIX_NAME;
        }

        /**
         * This is called when starting the refactoring
         *
         * @param project           current project
         * @param descriptor        information about the data clumps instance
         */
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiParameterList parameterList = (PsiParameterList) descriptor.getPsiElement();
            PsiGroup dataClumpsGroup = new PsiGroup();
            dataClumpsGroup.addConnection(parameterList);

            // find other parameter lists for the given one
            List<PsiParameterList> dataclumpPL = checkParameterDataclumps(parameterList, null);
            if (dataclumpPL.isEmpty()) {
                PsiUtils.log(LOGGER_NAME, "Parameter list Dataclump instances not found!");
                return;
            }

            PsiUtils.log(LOGGER_NAME, "dataclumpPL ===== " + dataclumpPL.size());

            // search for the common parameters between the methods
            List<PsiParameter> dataclumpsParameters = null;

            // search for the reported method in problem holder description and add them to dataclump group
            for (PsiParameterList pl : dataclumpPL) {
                if (descriptor.getDescriptionTemplate().contains(((PsiMethod) pl.getParent()).getName())) {
                    PsiUtils.log(LOGGER_NAME, "addConnection !!!!!!!!");
                    dataClumpsGroup.addConnection(pl);
                }
                if (dataclumpsParameters == null) {
                    // search for the common parameters between the methods
                    dataclumpsParameters = PsiUtils.getCommonParameters(parameterList, pl);
                }
            }

            if (dataClumpsGroup.getConnections().isEmpty()) {
                PsiUtils.log(LOGGER_NAME, "The other method in this dataclump instance could not found!");
                PsiUtils.log(LOGGER_NAME, "dataClumpsGroup.getConnections().size() = " + dataClumpsGroup.getConnections().size());
                PsiUtils.log(LOGGER_NAME, "descriptor.getDescriptionTemplate(): " + descriptor.getDescriptionTemplate());

                return;
            }

            PsiUtils.log(LOGGER_NAME, "dataclumpsParameters = " + dataclumpsParameters);
            // add parameters to dataclumps instance
            for (PsiParameter param : Objects.requireNonNull(dataclumpsParameters)) {
                dataClumpsGroup.addElement(param);
            }

            // call the refactoring service for dataclumps
            ApplicationManager.getApplication().invokeLater(
                    () -> DataclassRefactoringService.refactor(project, dataClumpsGroup));
        }


        @NotNull
        public String getFamilyName() {
            return QUICK_FIX_NAME;
        }
    }

    /**
     * Receives the detected data clumps fields instance and redirect the refactoring to DataclassRefactoringService
     */
    private class FieldsDataClumpFix implements LocalQuickFix {
        /**
         * Returns a name for this quick fix class
         * required by testing using LightJavaCodeInsightFixtureTestCase
         *
         * @return Quick fix name.
         */
        @NotNull
        @Override
        public String getName() {
            return QUICK_FIX_NAME;
        }

        /**
         * This is called when starting the refactoring
         *
         * @param project           current project
         * @param descriptor        information about the data clumps instance
         */
        public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
            PsiUtils.log(LOGGER_NAME, "descriptor.getStartElement() = " + descriptor.getStartElement().getClass());
            PsiUtils.log(LOGGER_NAME, "descriptor.getLineNumber() = " + descriptor.getLineNumber());
            PsiFile currentFile = (PsiFile) descriptor.getPsiElement();
            Collection<PsiClass> classesInFile = PsiTreeUtil.findChildrenOfType(currentFile, PsiClass.class);

            PsiUtils.log(LOGGER_NAME, "currentFile.getTextRange() = " + currentFile.getTextRange().toString());
            PsiUtils.log(LOGGER_NAME, "currentFile.getTextOffset() = " + currentFile.getTextOffset());
            PsiClass currentClass = extractClassFromDescriptor(project, descriptor.getLineNumber(), currentFile, classesInFile);
            if (currentClass == null) {
                PsiUtils.log(LOGGER_NAME, "The Class of the fields data clump instance not found!");
                return;
            }


            PsiGroup dataClumpsGroup = extractSmellyFields(descriptor, currentClass);

            // find the other class of this dc instance
            ArrayList<PsiClass> dataclumpClasses = checkFieldsDataclumps(currentClass, null);
            if (dataclumpClasses.isEmpty()) {
                PsiUtils.log(LOGGER_NAME, "The other classes of the fields data clump instance not found!");
                return;
            }

            // search for the reported fields in classes
            for (PsiClass dataclumpClass : dataclumpClasses) {
                boolean fieldFound = false;
                for (PsiElement elem : dataClumpsGroup.getElements()) {

                    if (dataclumpClass.findFieldByName(((PsiField) elem).getName(), true) == null) {
                        break;
                    }

                    fieldFound = true;

                }
                if (fieldFound) {
                    dataClumpsGroup.addConnection(dataclumpClass);
                    PsiUtils.log(LOGGER_NAME, "add dataclump Class: " + dataclumpClass.getName());
                }
            }
            if (dataClumpsGroup.getConnections().size() < 2) {
                PsiUtils.log(LOGGER_NAME, "The other classes in this data clump instance could not found!");
                PsiUtils.log(LOGGER_NAME, "dataClumpsGroup.getConnections().size() = " + dataClumpsGroup.getConnections().size());
                PsiUtils.log(LOGGER_NAME, "descriptor.getDescriptionTemplate(): " + descriptor.getDescriptionTemplate());

                return;
            }

            // call the refactoring service for dataclumps
            ApplicationManager.getApplication().invokeLater(
                    () -> DataclassRefactoringService.refactor(project, dataClumpsGroup));
        }

        /**
         * Extracts the smelly fields out of the problem descriptor and return the results as a PsiGroup
         *
         * @param descriptor            instance information
         * @param currentClass
         * @return
         */
        @NotNull
        private PsiGroup extractSmellyFields(@NotNull ProblemDescriptor descriptor, PsiClass currentClass) {
            PsiGroup dataClumpsGroup = new PsiGroup();
            // add the current class to this instance, the other class/classes will be added later
            dataClumpsGroup.addConnection(currentClass);

            //extract the smelly fields out of the problem descriptor
            for (PsiField currentField : currentClass.getFields()) {
                if (descriptor.getDescriptionTemplate().contains(currentField.getName())) {
                    dataClumpsGroup.addElement(currentField);

                    PsiUtils.log(LOGGER_NAME, "Dataclump Field: " + currentField.getName());

                }
            }
            return dataClumpsGroup;
        }

        /**
         * Extracts the class out of the problem descriptor. Returns the found class or null.
         *
         * @param project
         * @param descriptorLineNumber
         * @param currentFile
         * @param classesInFile
         * @return
         */
        @Nullable
        private PsiClass extractClassFromDescriptor(@NotNull Project project, int descriptorLineNumber, PsiFile currentFile, Collection<PsiClass> classesInFile) {
            PsiClass currentClass = null;
            for (PsiClass c : classesInFile) {
                if (Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(currentFile)).getLineNumber(c.getTextOffset()) <= descriptorLineNumber) {
                    currentClass = c;
                } else {
                    PsiUtils.log(LOGGER_NAME, "Class " + c.getName() + " with ???= " + Objects.requireNonNull(PsiDocumentManager.getInstance(project).getDocument(currentFile)).getLineNumber(c.getTextOffset()) + " doesn't include line number " + descriptorLineNumber);
                }
            }
            return currentClass;
        }


        @NotNull
        public String getFamilyName() {
            return QUICK_FIX_NAME;
        }
    }

}
