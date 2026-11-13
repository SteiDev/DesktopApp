#!/bin/bash
javac --module-path /home/bleh/Documents/Uni/extraShit/javafx-sdk-27.0.1/lib --add-modules javafx.controls,javafx.fxml *.java
java --module-path /home/bleh/Documents/Uni/extraShit/javafx-sdk-27.0.1/lib --add-modules javafx.controls,javafx.fxml PreviewApp