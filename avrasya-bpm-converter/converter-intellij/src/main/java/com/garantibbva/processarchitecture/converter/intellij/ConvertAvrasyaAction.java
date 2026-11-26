package com.garantibbva.processarchitecture.converter.intellij;

import com.garantibbva.processarchitecture.converter.ConversionResult;
import com.garantibbva.processarchitecture.converter.Converter;
import com.garantibbva.processarchitecture.converter.ConverterOptions;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.jetbrains.annotations.NotNull;

/**
 * XML'e sag tik (Project view / editor) ya da Tools menusu: Avrasya workflow XML'ini
 * BAMOE BPM projesine (kjar + service) donusturur.
 */
public final class ConvertAvrasyaAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile f = e.getData(CommonDataKeys.VIRTUAL_FILE);
        // Tools menusunde her zaman acik (dosya sorulur); baglam menulerinde yalnizca .xml
        boolean toolsMenu = "MainMenu".equals(e.getPlace()) || e.getPlace().contains("Tools");
        e.getPresentation().setEnabledAndVisible(toolsMenu || (f != null && !f.isDirectory() && "xml".equalsIgnoreCase(f.getExtension())));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile xml = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (xml == null || xml.isDirectory() || !"xml".equalsIgnoreCase(xml.getExtension())) {
            xml = FileChooser.chooseFile(new FileChooserDescriptor(true, false, false, false, false, false)
                    .withFileFilter(vf -> "xml".equalsIgnoreCase(vf.getExtension())).withTitle("Avrasya workflow XML"), project, null);
            if (xml == null) {
                return;
            }
        }
        String defaultOut = new java.io.File(xml.getParent() != null ? xml.getParent().getPath() : System.getProperty("user.home"), "bpm-out").getPath();
        ConvertDialog dlg = new ConvertDialog(project, defaultOut);
        if (!dlg.showAndGet()) {
            return;
        }
        ConverterOptions options = dlg.options();
        Path out = Paths.get(dlg.outputDir());
        VirtualFile source = xml;

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Avrasya -> BAMOE BPM donusumu", false) {
            private ConversionResult result;
            private Path written;
            private Throwable error;

            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    String text = new String(source.contentsToByteArray(), StandardCharsets.UTF_8);
                    result = new Converter(options).convert(text);
                    written = result.writeTo(out);
                } catch (Throwable t) {
                    error = t;
                }
            }

            @Override
            public void onFinished() {
                if (error != null) {
                    Messages.showErrorDialog(project, String.valueOf(error.getMessage()), "Donusum basarisiz");
                    return;
                }
                VirtualFile root = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(written);
                if (root != null) {
                    root.refresh(true, true);
                }
                String msg = String.join("<br>", result.info) + "<br>Uyari: " + result.warnings.size() + "<br>" + written;
                com.intellij.notification.Notification n = NotificationGroupManager.getInstance()
                        .getNotificationGroup("Avrasya BPM Converter")
                        .createNotification("Donusum tamamlandi", msg, result.warnings.isEmpty() ? NotificationType.INFORMATION : NotificationType.WARNING);
                n.addAction(NotificationAction.createSimple("Raporu ac", () -> {
                    VirtualFile report = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(written.resolve("CONVERSION_REPORT.md"));
                    if (report != null && project != null) {
                        FileEditorManager.getInstance(project).openFile(report, true);
                    }
                }));
                n.notify(project);
            }
        });
    }
}
