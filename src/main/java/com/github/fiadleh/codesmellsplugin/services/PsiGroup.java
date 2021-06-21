package com.github.fiadleh.codesmellsplugin.services;

import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A container for the various PSI elements that form a code smell instance
 *
 * @author Firas Adleh
 */
public class PsiGroup {
    /**
     * Code smell elements that need to be refactored
     */
    private final List<PsiElement> elements;

    /**
     * Connections between the code smell elements
     */
    private final List<PsiElement> connections;

    public PsiGroup(PsiElement... elements) {
        this.elements = new ArrayList<>();
        this.connections = new ArrayList<>();
        this.elements.addAll(Arrays.asList(elements));
    }

    public boolean hasElement(PsiElement element) {
        for (PsiElement elem:this.elements){
            if(element.getText().equals(elem.getText())){
                return true;
            }
        }
        return false;
    }

    public void addConnection(PsiElement connection) {
        connections.add(connection);
    }

    public void addElement(PsiElement element) {
        if (!this.hasElement(element)) {
            this.elements.add(element);
        }
    }

    public List<PsiElement> getConnections() {
        return connections;
    }

    public List<PsiElement> getElements() {
        return this.elements;
    }


    public PsiElement getElement(int index){
        return this.elements.get(index);
    }


}
