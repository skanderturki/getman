package com.getman.ui.widgets;

import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.StackLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;

import com.getman.core.model.Auth;
import com.getman.core.model.AuthType;

/** Editor for a request's {@link Auth}: a type selector and the fields of the selected type. */
public class AuthPanel extends Composite {
	private static final char MASK = '•';

	private final Auth auth;
	private final Runnable onChange;
	private final Composite stack;
	private final StackLayout stackLayout = new StackLayout();
	private final Control nonePage;
	private final Control bearerPage;
	private final Control basicPage;

	public AuthPanel(Composite parent, Auth auth, Runnable onChange) {
		super(parent, SWT.NONE);
		this.auth = auth;
		this.onChange = onChange;
		setLayout(new GridLayout(2, false));

		new Label(this, SWT.NONE).setText("Type:");
		Combo typeCombo = new Combo(this, SWT.READ_ONLY);
		for (AuthType t : AuthType.values()) {
			typeCombo.add(t.label());
		}
		typeCombo.select(auth.getType().ordinal());

		stack = new Composite(this, SWT.NONE);
		stack.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 2, 1));
		stack.setLayout(stackLayout);
		nonePage = createNonePage(stack);
		bearerPage = createBearerPage(stack);
		basicPage = createBasicPage(stack);

		typeCombo.addListener(SWT.Selection, e -> {
			auth.setType(AuthType.values()[typeCombo.getSelectionIndex()]);
			showPage();
			onChange.run();
		});
		showPage();
	}

	private void showPage() {
		stackLayout.topControl = switch (auth.getType()) {
		case BEARER -> bearerPage;
		case BASIC -> basicPage;
		default -> nonePage;
		};
		stack.layout();
	}

	private static Control createNonePage(Composite parent) {
		Composite page = page(parent, 1);
		hint(page, "This request does not send any credentials.", 1);
		return page;
	}

	private Control createBearerPage(Composite parent) {
		Composite page = page(parent, 3);
		new Label(page, SWT.NONE).setText("Token:");
		Text token = secretField(page, auth.getToken());
		token.setMessage("e.g. eyJhbGciOi…");
		token.addListener(SWT.Modify, e -> {
			auth.setToken(token.getText());
			onChange.run();
		});
		showToggle(page, token);
		hint(page, "Sent as \"Authorization: Bearer <token>\".", 3);
		return page;
	}

	private Control createBasicPage(Composite parent) {
		Composite page = page(parent, 3);
		new Label(page, SWT.NONE).setText("Username:");
		Text user = new Text(page, SWT.SINGLE | SWT.BORDER);
		user.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 2, 1));
		user.setText(auth.getUsername());
		user.addListener(SWT.Modify, e -> {
			auth.setUsername(user.getText());
			onChange.run();
		});
		new Label(page, SWT.NONE).setText("Password:");
		Text password = secretField(page, auth.getPassword());
		password.addListener(SWT.Modify, e -> {
			auth.setPassword(password.getText());
			onChange.run();
		});
		showToggle(page, password);
		hint(page, "Sent as \"Authorization: Basic <base64 of username:password>\".", 3);
		return page;
	}

	private static Composite page(Composite parent, int columns) {
		Composite page = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout(columns, false);
		layout.marginWidth = 0;
		page.setLayout(layout);
		return page;
	}

	private static Text secretField(Composite page, String value) {
		Text text = new Text(page, SWT.SINGLE | SWT.BORDER);
		text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
		text.setEchoChar(MASK);
		text.setText(value);
		return text;
	}

	private static void showToggle(Composite page, Text secret) {
		Button show = new Button(page, SWT.CHECK);
		show.setText("Show");
		show.addListener(SWT.Selection, e -> secret.setEchoChar(show.getSelection() ? '\0' : MASK));
	}

	private static void hint(Composite page, String text, int span) {
		Label label = new Label(page, SWT.WRAP);
		label.setText(text + "\nA header named Authorization in the Headers tab takes precedence. "
				+ "Credentials are stored in this workspace's GetMan state file.");
		label.setForeground(page.getDisplay().getSystemColor(SWT.COLOR_WIDGET_DARK_SHADOW));
		GridData data = new GridData(SWT.FILL, SWT.TOP, true, false, span, 1);
		data.widthHint = 300;
		data.verticalIndent = 6;
		label.setLayoutData(data);
	}
}
