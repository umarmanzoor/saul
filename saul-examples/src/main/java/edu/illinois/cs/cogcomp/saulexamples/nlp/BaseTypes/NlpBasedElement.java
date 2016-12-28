package edu.illinois.cs.cogcomp.saulexamples.nlp.BaseTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by Taher on 2016-12-18.
 */
public abstract class NlpBasedElement extends SpanBasedElement {
    private String id;
    private String text;
    private Map<String, List<String>> properties = new HashMap<>();

    public NlpBasedElement() {
        setStart(-1);
        setEnd(-1);
    }

    public NlpBasedElement(String id, Integer start, Integer end, String text) {
        this.setId(id);
        this.setStart(start);
        this.setEnd(end);
        this.setText(text);
    }

    public abstract NlpBaseElementTypes getType();

    public boolean containsProperty(String name) {
        return properties.containsKey(name);
    }

    public String getPropertyValue(String name) {
        if (properties.containsKey(name))
            return properties.get(name).get(0);
        return null;
    }
    public List<String> getPropertyValues(String name) {
        if (properties.containsKey(name))
            return properties.get(name);
        return new ArrayList<>();
    }
    public void addPropertyValue(String name, String value) {
        if(!containsProperty(name))
            properties.put(name, new ArrayList<>());
        properties.get(name).add(value);
    }
    public void removeProperty(String name){
        if(containsProperty(name))
            properties.remove(name);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public static NlpBasedElement create(NlpBaseElementTypes type) {

        switch (type) {
            case Document:
                return new Document();
            case Sentence:
                return new Sentence();
            case Phrase:
                return new Phrase();
            case Token:
                return new Token();
        }
        return null;
    }

    @Override
    public String toString() {
        return getText();
    }
}