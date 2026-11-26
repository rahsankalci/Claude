package com.garantibbva.processarchitecture.converter.ui;

import com.garantibbva.processarchitecture.converter.ConversionResult;
import com.garantibbva.processarchitecture.converter.Converter;
import com.garantibbva.processarchitecture.converter.ConverterOptions;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.UIManager;

/** Masaustu arayuz: Avrasya XML'ini yukle, ayarlari gir, Convert. */
public final class ConverterApp extends JFrame {

    private static final Path SETTINGS = Paths.get(System.getProperty("user.home"), ".avrasya-bpm-converter.properties");

    private final JTextField xmlField = new JTextField(40);
    private final JTextField outField = new JTextField(40);
    private final JTextField groupId = new JTextField();
    private final JTextField artifactId = new JTextField();
    private final JTextField version = new JTextField();
    private final JTextField basePackage = new JTextField();
    private final JCheckBox useArk = new JCheckBox("ark-bpm'e bagla (onerilen)", true);
    private final JTextField kjarParentG = new JTextField();
    private final JTextField kjarParentA = new JTextField();
    private final JTextField kjarParentV = new JTextField();
    private final JTextField svcParentG = new JTextField();
    private final JTextField svcParentA = new JTextField();
    private final JTextField svcParentV = new JTextField();
    private final JTextField svcDeps = new JTextField();
    private final JTextField kjarDeps = new JTextField();
    private final JTextField kieVersion = new JTextField();
    private final JTextArea log = new JTextArea();
    private final DefaultListModel<String> fileModel = new DefaultListModel<>();
    private final JList<String> fileList = new JList<>(fileModel);
    private final JTextArea preview = new JTextArea();
    private final JButton convertBtn = new JButton("Convert");
    private ConversionResult last;

    private ConverterApp() {
        super("Avrasya (Oracle Workflow) -> BAMOE BPM Donusturucu");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        load(ConverterOptions.load(SETTINGS));

        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        row(top, 0, "Workflow XML", xmlField, button("Sec...", e -> chooseXml()));
        row(top, 1, "Cikti klasoru", outField, button("Sec...", e -> chooseOut()));

        JTabbedPane settings = new JTabbedPane();
        JPanel project = form(new String[] {"groupId", "artifactId (bos: surec adindan)", "version", "Java paketi (bos: otomatik)"},
                groupId, artifactId, version, basePackage);
        JPanel ark = form(new String[] {"kjar parent groupId", "kjar parent artifactId", "kjar parent version",
                        "service parent groupId", "service parent artifactId", "service parent version",
                        "service ark-bpm bagimliliklari (virgullu)", "kjar bagimliliklari (virgullu)", "KIE surumu (ark-bpm kapaliyken)"},
                kjarParentG, kjarParentA, kjarParentV, svcParentG, svcParentA, svcParentV, svcDeps, kjarDeps, kieVersion);
        JPanel arkWrap = new JPanel(new BorderLayout());
        arkWrap.add(useArk, BorderLayout.NORTH);
        arkWrap.add(ark, BorderLayout.CENTER);
        settings.addTab("Proje", project);
        settings.addTab("ark-bpm baglantisi", arkWrap);

        JPanel actions = new JPanel();
        convertBtn.setFont(convertBtn.getFont().deriveFont(Font.BOLD));
        convertBtn.addActionListener(e -> convert());
        actions.add(convertBtn);
        actions.add(button("Ayarlari kaydet", e -> save()));
        actions.add(button("Cikti klasorunu ac", e -> openOut()));

        log.setEditable(false);
        log.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        preview.setEditable(false);
        preview.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        fileList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && last != null && fileList.getSelectedValue() != null) {
                preview.setText(last.files.get(fileList.getSelectedValue()));
                preview.setCaretPosition(0);
            }
        });
        JSplitPane files = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(fileList), new JScrollPane(preview));
        files.setDividerLocation(320);
        JTabbedPane results = new JTabbedPane();
        results.addTab("Sonuc / Uyarilar", new JScrollPane(log));
        results.addTab("Uretilen dosyalar", files);

        JPanel north = new JPanel(new BorderLayout());
        north.add(top, BorderLayout.NORTH);
        north.add(settings, BorderLayout.CENTER);
        north.add(actions, BorderLayout.SOUTH);
        getContentPane().add(north, BorderLayout.NORTH);
        getContentPane().add(results, BorderLayout.CENTER);
        setPreferredSize(new Dimension(1000, 760));
        pack();
        setLocationRelativeTo(null);

        new DropTarget(this, new java.awt.dnd.DropTargetAdapter() {
            @Override
            @SuppressWarnings("unchecked")
            public void drop(DropTargetDropEvent ev) {
                try {
                    ev.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> fs = (List<File>) ev.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    if (!fs.isEmpty()) {
                        setXml(fs.get(0));
                    }
                } catch (Exception ex) {
                    log.setText("Dosya birakilamadi: " + ex.getMessage());
                }
            }
        });
        log.setText("Workflow XML'ini sec (ya da pencereye surukle), ayarlari kontrol et ve Convert'e bas.\n");
    }

    // ------------------------------------------------------------------ UI yardimcilari

    private static JButton button(String t, java.awt.event.ActionListener l) {
        JButton b = new JButton(t);
        b.addActionListener(l);
        return b;
    }

    private static void row(JPanel p, int y, String label, JTextField f, JButton b) {
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.gridy = y;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        p.add(new JLabel(label), c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        p.add(f, c);
        c.gridx = 2;
        c.weightx = 0;
        c.fill = GridBagConstraints.NONE;
        p.add(b, c);
    }

    private static JPanel form(String[] labels, JTextField... fields) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        for (int i = 0; i < labels.length; i++) {
            GridBagConstraints c = new GridBagConstraints();
            c.insets = new Insets(2, 2, 2, 8);
            c.gridy = i;
            c.anchor = GridBagConstraints.WEST;
            p.add(new JLabel(labels[i]), c);
            c.gridx = 1;
            c.weightx = 1;
            c.fill = GridBagConstraints.HORIZONTAL;
            p.add(fields[i], c);
        }
        JPanel wrap = new JPanel(new BorderLayout());
        wrap.add(p, BorderLayout.NORTH);
        return wrap;
    }

    // ------------------------------------------------------------------ ayarlar

    private void load(ConverterOptions o) {
        groupId.setText(o.groupId);
        artifactId.setText(o.artifactId);
        version.setText(o.version);
        basePackage.setText(o.basePackage);
        useArk.setSelected(o.useArkBpm);
        kjarParentG.setText(o.kjarParentGroupId);
        kjarParentA.setText(o.kjarParentArtifactId);
        kjarParentV.setText(o.kjarParentVersion);
        svcParentG.setText(o.serviceParentGroupId);
        svcParentA.setText(o.serviceParentArtifactId);
        svcParentV.setText(o.serviceParentVersion);
        svcDeps.setText(o.serviceDependencies);
        kjarDeps.setText(o.kjarDependencies);
        kieVersion.setText(o.kieVersion);
    }

    private ConverterOptions options() {
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

    private void save() {
        try (OutputStream out = Files.newOutputStream(SETTINGS)) {
            options().save(out);
            log.append("Ayarlar kaydedildi: " + SETTINGS + "\n");
        } catch (Exception e) {
            log.append("Ayarlar kaydedilemedi: " + e.getMessage() + "\n");
        }
    }

    // ------------------------------------------------------------------ islemler

    private void chooseXml() {
        JFileChooser fc = new JFileChooser(xmlField.getText().isBlank() ? null : new File(xmlField.getText()).getParentFile());
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setXml(fc.getSelectedFile());
        }
    }

    private void setXml(File f) {
        xmlField.setText(f.getAbsolutePath());
        if (outField.getText().isBlank()) {
            outField.setText(new File(f.getParentFile(), "bpm-out").getAbsolutePath());
        }
    }

    private void chooseOut() {
        JFileChooser fc = new JFileChooser(outField.getText().isBlank() ? null : new File(outField.getText()));
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            outField.setText(fc.getSelectedFile().getAbsolutePath());
        }
    }

    private void openOut() {
        try {
            File d = new File(outField.getText());
            if (d.isDirectory()) {
                java.awt.Desktop.getDesktop().open(d);
            }
        } catch (Exception e) {
            log.append("Klasor acilamadi: " + e.getMessage() + "\n");
        }
    }

    private void convert() {
        if (xmlField.getText().isBlank() || outField.getText().isBlank()) {
            JOptionPane.showMessageDialog(this, "Workflow XML ve cikti klasoru secin.");
            return;
        }
        Path xml = Paths.get(xmlField.getText());
        Path out = Paths.get(outField.getText());
        ConverterOptions o = options();
        convertBtn.setEnabled(false);
        log.setText("Donusturuluyor: " + xml + "\n");
        new SwingWorker<ConversionResult, Void>() {
            Path written;

            @Override
            protected ConversionResult doInBackground() throws Exception {
                ConversionResult r = new Converter(o).convert(new String(Files.readAllBytes(xml), StandardCharsets.UTF_8));
                written = r.writeTo(out);
                return r;
            }

            @Override
            protected void done() {
                convertBtn.setEnabled(true);
                try {
                    last = get();
                    StringBuilder sb = new StringBuilder();
                    last.info.forEach(i -> sb.append(i).append('\n'));
                    sb.append("\nProje yazildi: ").append(written).append("\n");
                    sb.append("Rapor: ").append(written.resolve("CONVERSION_REPORT.md")).append("\n\n");
                    sb.append("Uyarilar (").append(last.warnings.size()).append("):\n");
                    last.warnings.forEach(w -> sb.append("  ! ").append(w).append('\n'));
                    log.setText(sb.toString());
                    fileModel.clear();
                    for (Map.Entry<String, String> e : last.files.entrySet()) {
                        fileModel.addElement(e.getKey());
                    }
                } catch (Exception ex) {
                    Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                    log.setText("HATA: " + c.getMessage() + "\n");
                }
            }
        }.execute();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // varsayilan gorunum
            }
            new ConverterApp().setVisible(true);
        });
    }
}
