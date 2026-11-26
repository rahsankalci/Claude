package com.garantibbva.processarchitecture.converter.intellij;

import com.garantibbva.processarchitecture.converter.ConverterOptions;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.Nullable;

/** Donusum ayarlari (PropertiesComponent ile kalici). */
final class ConvertDialog extends DialogWrapper {

    private static final String P = "avrasya.bpm.";
    private final PropertiesComponent props = PropertiesComponent.getInstance();
    private final TextFieldWithBrowseButton outDir = new TextFieldWithBrowseButton();
    private final JBTextField groupId = new JBTextField();
    private final JBTextField artifactId = new JBTextField();
    private final JBTextField version = new JBTextField();
    private final JBTextField basePackage = new JBTextField();
    private final JBCheckBox useArk = new JBCheckBox("ark-bpm'e bagla (onerilen)", true);
    private final JBTextField kjarParentG = new JBTextField();
    private final JBTextField kjarParentA = new JBTextField();
    private final JBTextField kjarParentV = new JBTextField();
    private final JBTextField svcParentG = new JBTextField();
    private final JBTextField svcParentA = new JBTextField();
    private final JBTextField svcParentV = new JBTextField();
    private final JBTextField svcDeps = new JBTextField();
    private final JBTextField kjarDeps = new JBTextField();
    private final JBTextField kieVersion = new JBTextField();

    ConvertDialog(@Nullable Project project, String defaultOut) {
        super(project, true);
        setTitle("Avrasya -> BAMOE BPM");
        ConverterOptions d = new ConverterOptions();
        outDir.addBrowseFolderListener("Cikti klasoru", null, project, FileChooserDescriptorFactory.createSingleFolderDescriptor());
        outDir.setText(props.getValue(P + "out", defaultOut));
        groupId.setText(props.getValue(P + "groupId", d.groupId));
        artifactId.setText(props.getValue(P + "artifactId", ""));
        version.setText(props.getValue(P + "version", d.version));
        basePackage.setText(props.getValue(P + "basePackage", ""));
        useArk.setSelected(props.getBoolean(P + "useArk", true));
        kjarParentG.setText(props.getValue(P + "kjarParentG", d.kjarParentGroupId));
        kjarParentA.setText(props.getValue(P + "kjarParentA", d.kjarParentArtifactId));
        kjarParentV.setText(props.getValue(P + "kjarParentV", d.kjarParentVersion));
        svcParentG.setText(props.getValue(P + "svcParentG", d.serviceParentGroupId));
        svcParentA.setText(props.getValue(P + "svcParentA", d.serviceParentArtifactId));
        svcParentV.setText(props.getValue(P + "svcParentV", d.serviceParentVersion));
        svcDeps.setText(props.getValue(P + "svcDeps", d.serviceDependencies));
        kjarDeps.setText(props.getValue(P + "kjarDeps", d.kjarDependencies));
        kieVersion.setText(props.getValue(P + "kieVersion", d.kieVersion));
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        JPanel p = FormBuilder.createFormBuilder()
                .addLabeledComponent("Cikti klasoru", outDir)
                .addSeparator()
                .addLabeledComponent("groupId", groupId)
                .addLabeledComponent("artifactId (bos: surec adindan)", artifactId)
                .addLabeledComponent("version", version)
                .addLabeledComponent("Java paketi (bos: otomatik)", basePackage)
                .addSeparator()
                .addComponent(useArk)
                .addLabeledComponent("kjar parent groupId", kjarParentG)
                .addLabeledComponent("kjar parent artifactId", kjarParentA)
                .addLabeledComponent("kjar parent version", kjarParentV)
                .addLabeledComponent("service parent groupId", svcParentG)
                .addLabeledComponent("service parent artifactId", svcParentA)
                .addLabeledComponent("service parent version", svcParentV)
                .addLabeledComponent("service ark-bpm bagimliliklari (virgullu)", svcDeps)
                .addLabeledComponent("kjar bagimliliklari (virgullu)", kjarDeps)
                .addLabeledComponent("KIE surumu (ark-bpm kapaliyken)", kieVersion)
                .getPanel();
        return p;
    }

    String outputDir() {
        return outDir.getText().trim();
    }

    ConverterOptions options() {
        props.setValue(P + "out", outputDir());
        props.setValue(P + "groupId", groupId.getText().trim());
        props.setValue(P + "artifactId", artifactId.getText().trim());
        props.setValue(P + "version", version.getText().trim());
        props.setValue(P + "basePackage", basePackage.getText().trim());
        props.setValue(P + "useArk", useArk.isSelected(), true);
        props.setValue(P + "kjarParentG", kjarParentG.getText().trim());
        props.setValue(P + "kjarParentA", kjarParentA.getText().trim());
        props.setValue(P + "kjarParentV", kjarParentV.getText().trim());
        props.setValue(P + "svcParentG", svcParentG.getText().trim());
        props.setValue(P + "svcParentA", svcParentA.getText().trim());
        props.setValue(P + "svcParentV", svcParentV.getText().trim());
        props.setValue(P + "svcDeps", svcDeps.getText().trim());
        props.setValue(P + "kjarDeps", kjarDeps.getText().trim());
        props.setValue(P + "kieVersion", kieVersion.getText().trim());

        ConverterOptions o = new ConverterOptions();
        o.groupId = groupId.getText().trim();
        o.artifactId = artifactId.getText().trim();
        o.version = version.getText().trim();
        o.basePackage = basePackage.getText().trim();
        o.useArkBpm = useArk.isSelected();
        o.kjarParentGroupId = kjarParentG.getText().trim();
        o.kjarParentArtifactId = kjarParentA.getText().trim();
        o.kjarParentVersion = kjarParentV.getText().trim();
        o.serviceParentGroupId = svcParentG.getText().trim();
        o.serviceParentArtifactId = svcParentA.getText().trim();
        o.serviceParentVersion = svcParentV.getText().trim();
        o.serviceDependencies = svcDeps.getText().trim();
        o.kjarDependencies = kjarDeps.getText().trim();
        o.kieVersion = kieVersion.getText().trim();
        return o;
    }
}
