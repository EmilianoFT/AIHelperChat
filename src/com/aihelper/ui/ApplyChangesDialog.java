package com.aihelper.ui;

import org.eclipse.swt.*;
import org.eclipse.swt.widgets.*;

public class ApplyChangesDialog {

    public static boolean confirm(Shell shell, String diff) {
        MessageBox box = new MessageBox(shell, SWT.YES | SWT.NO | SWT.ICON_WARNING);
        box.setText("Apply AI Changes?");
        box.setMessage(diff);
        return box.open() == SWT.YES;
    }
}