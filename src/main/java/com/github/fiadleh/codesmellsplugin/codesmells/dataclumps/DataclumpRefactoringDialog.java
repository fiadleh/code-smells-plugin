package com.github.fiadleh.codesmellsplugin.codesmells.dataclumps;

import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Responsible for creating the refactoring dialog with more details about the data clumps instance
 *
 * @author Firas Adleh
 */
public class DataclumpRefactoringDialog extends DialogWrapper {
    private JTextField textFieldNewClassName;
    private final JLabel labelMessage;
    private final String newClassName;
    List<String> variables;
    List<String> files;


    /**
     * Constructor to initialize the values required for the dialog.
     *
     * @param className
     * @param message
     * @param variables
     * @param files
     */
    public DataclumpRefactoringDialog(String className, String message, List<String> variables, List<String> files) {
        super(true); // use current window as parent
        setTitle("Dataclump Refactoring Dialog");
        newClassName = className;
        labelMessage = new JLabel(message);
        labelMessage.setPreferredSize(new Dimension(500, 30));

        this.setOKButtonText("Refactor");
        this.variables = variables;
        this.files = files;

        // init call  at the end of the constructor
        init();
    }

    /**
     * Creates all GUI elements for information and the field to edit the new class name.
     *
     * @return      JComponent including all swing elements.
     */
    @Nullable
    @Override
    protected JComponent createCenterPanel() {

        JPanel dialogPanel = new JPanel(new GridBagLayout());

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.WEST;
        constraints.gridy = 0;

        JLabel varLabel = new JLabel("Duplicated Variables:");
        varLabel.setPreferredSize(new Dimension(500, 30));

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        dialogPanel.add(varLabel, constraints);

        for (String variable:variables){
            JLabel label1 = new JLabel(variable);
            label1.setPreferredSize(new Dimension(500, 30));

            constraints.gridx = 0;
            constraints.gridy++;
            constraints.gridwidth = 2;
            dialogPanel.add(label1, constraints);
        }

        JLabel filesLabel = new JLabel("Occurrences:");
        filesLabel.setPreferredSize(new Dimension(700, 30));
        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        dialogPanel.add(filesLabel, constraints);

        for (String file:files){
            JLabel label2 = new JLabel(file);
            label2.setPreferredSize(new Dimension(800, 30));
            constraints.gridx = 0;
            constraints.gridy++;
            constraints.gridwidth = 2;
            dialogPanel.add(label2, constraints);
        }


        JLabel label = new JLabel("New class name:");
        label.setPreferredSize(new Dimension(500, 30));

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        dialogPanel.add(label, constraints);

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        dialogPanel.add(labelMessage, constraints);

        textFieldNewClassName = new JTextField();
        textFieldNewClassName.setText(this.newClassName);
        textFieldNewClassName.setPreferredSize(new Dimension(400, 30));

        constraints.gridx = 0;
        constraints.gridy++;
        constraints.gridwidth = 2;
        dialogPanel.add(textFieldNewClassName, constraints);

        return dialogPanel;
    }

    public String getNewClassName(){
        return textFieldNewClassName.getText();
    }
}
