package com.github.qmulda.dependencyanalyser.toolWindow;

import com.github.qmulda.dependencyanalyser.dependencyhandler.DependencyHandler;
import com.github.qmulda.dependencyanalyser.services.SupabaseExporter;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBPanel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import com.intellij.ui.table.JBTable;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;

/**
 * ToolWindowFactory for the Dependency Analyser tool window.
 * This creates the sidebar UI component that displays dependency scan results
 * in a table format.
 */
public class MyToolWindowFactory implements ToolWindowFactory {

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        System.out.println("FACTORY CALLED");
        MyToolWindow myToolWindow = new MyToolWindow(project);
        Content content = ContentFactory.getInstance().createContent(myToolWindow.getContent(), null, false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) {
        return true;
    }

    public static class MyToolWindow {
        private final JBPanel<?> content;
        private final JBLabel statusLabel;
        private static final Logger LOG = Logger.getInstance(MyToolWindow.class);

        public MyToolWindow(Project project) {
            this.content = new JBPanel<>(new BorderLayout(10, 10));
            this.content.setBorder(JBUI.Borders.empty(10));

            // Create table model first so it can be passed to the handler
            DefaultTableModel tableModel = createDependencyTableModel();
            JBPanel<?> headerPanel = getPanel(project, this.content, tableModel);

            // Create table for dependencies
            JBTable dependencyTable = new JBTable(tableModel);
            dependencyTable.setAutoResizeMode(JBTable.AUTO_RESIZE_ALL_COLUMNS);
            JBScrollPane scrollPane = new JBScrollPane(dependencyTable);
            scrollPane.setPreferredSize(new Dimension(600, 400));

            // Add components to content
            this.content.add(headerPanel, BorderLayout.NORTH);
            this.content.add(scrollPane, BorderLayout.CENTER);

            // Add status label at bottom
            this.statusLabel = new JBLabel("Ready to scan...");
            this.content.add(statusLabel, BorderLayout.SOUTH);
        }

        private static @NotNull JBPanel<?> getPanel(Project project, JBPanel<?> content, DefaultTableModel tableModel) {
            DependencyHandler handler = new DependencyHandler(project, content, tableModel);

            // Create header panel with title and scan button
            JBPanel<?> headerPanel = new JBPanel<>(new BorderLayout());
            JBLabel titleLabel = new JBLabel("Dependency scan results");
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD));
            headerPanel.add(titleLabel, BorderLayout.WEST);

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
            JButton scanButton = new JButton("Scan Project");
            scanButton.addActionListener(e -> handler.performScan());
            buttonPanel.add(scanButton);

            JButton exportButton = new JButton("Export to Cloud");
            SupabaseExporter exporter = new SupabaseExporter(project);
            exportButton.addActionListener(e -> exporter.exportAll(content));
            buttonPanel.add(exportButton);

            headerPanel.add(buttonPanel, BorderLayout.EAST);
            return headerPanel;
        }

        private DefaultTableModel createDependencyTableModel() {
            String[] columnNames = {"Group ID", "Artifact ID", "Version", "Scope", "Transitive"};
            return new DefaultTableModel(columnNames, 0) {
                @Override
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
        }

        public JBPanel<?> getContent() {
            return this.content;
        }
    }
}
