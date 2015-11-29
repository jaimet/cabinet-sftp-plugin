package com.afollestad.cabinetsftp;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.afollestad.cabinet.plugins.PluginAuthenticator;
import com.afollestad.cabinetsftp.api.SftpAccount;
import com.afollestad.cabinetsftp.dialogs.FileChooserDialog;
import com.afollestad.cabinetsftp.dialogs.ProgressDialogFragment;
import com.afollestad.cabinetsftp.sql.AccountProvider;
import com.afollestad.materialdialogs.MaterialDialog;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;

import java.io.File;

/**
 * @author Aidan Follestad (afollestad)
 */
public class AuthenticationActivity extends PluginAuthenticator
        implements View.OnClickListener, FileChooserDialog.Callback {

    private Button testConnection;
    private TextView host;
    private TextView port;
    private TextView user;
    private TextView pass;
    private CheckBox useSshKey;
    private TextView sshKeyPath;
    private TextView sshKeyPass;
    private TextView initialPath;
    private View sshKeyFrame;

    private boolean mTestedConnection;
    private Thread mThread;
    private ProgressDialogFragment mProgress;

    //TODO: Activity is killed onPause
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
            new MaterialDialog.Builder(this)
                    .title(R.string.permission_needed)
                    .content(R.string.permission_needed_desc)
                    .cancelable(false)
                    .positiveText(android.R.string.ok)
                    .show();
        } else {
            browseSshKey();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authentication);

        //noinspection ConstantConditions
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        testConnection = (Button) findViewById(R.id.testConnection);
        host = (TextView) findViewById(R.id.host);
        port = (TextView) findViewById(R.id.port);
        user = (TextView) findViewById(R.id.user);
        pass = (TextView) findViewById(R.id.pass);
        useSshKey = (CheckBox) findViewById(R.id.checkUseKey);
        sshKeyPath = (TextView) findViewById(R.id.sshKeyPath);
        sshKeyPass = (TextView) findViewById(R.id.sshKeyPassphrase);
        initialPath = (TextView) findViewById(R.id.initialPath);
        sshKeyFrame = findViewById(R.id.sshKeyFrame);

        final String lastSshKey = PreferenceManager.getDefaultSharedPreferences(AuthenticationActivity.this)
                .getString("last_ssh_key", null);
        useSshKey.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                mTestedConnection = false;
                sshKeyFrame.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                pass.setVisibility(isChecked ? View.GONE : View.VISIBLE);
                findViewById(R.id.passwordLabel).setVisibility(isChecked ? View.GONE : View.VISIBLE);
                invalidateTestConnectionEnabled();
            }
        });
        if (lastSshKey != null) {
            useSshKey.setChecked(true);
            sshKeyPath.setText("");
            sshKeyPath.append(lastSshKey);
        }

        TextWatcher watcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                mTestedConnection = false;
                invalidateTestConnectionEnabled();
                invalidateOptionsMenu();
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        };

        host.addTextChangedListener(watcher);
        port.addTextChangedListener(watcher);
        user.addTextChangedListener(watcher);
        pass.addTextChangedListener(watcher);
        sshKeyPath.addTextChangedListener(watcher);
        sshKeyPass.addTextChangedListener(watcher);
        initialPath.addTextChangedListener(watcher);
        testConnection.setOnClickListener(this);

        findViewById(R.id.browseSshKey).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ContextCompat.checkSelfPermission(AuthenticationActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                        PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(AuthenticationActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 69);
                    return;
                }

                browseSshKey();
            }
        });

        if (isSettings() && savedInstanceState == null) {
            final String accountId = getAccountId();
            SftpAccount account = AccountProvider.get(this, accountId);
            if (account == null) {
                finish(null);
                return;
            }
            host.setText(account.host);
            port.setText(account.port + "");
            user.setText(account.username);
            if (account.sshKeyPath != null && !account.sshKeyPath.trim().isEmpty()) {
                sshKeyPath.setText(account.sshKeyPath);
                sshKeyPass.setText(account.sshKeyPassword);
                pass.setText("");
                useSshKey.setChecked(true);
            } else {
                sshKeyPath.setText("");
                sshKeyPass.setText("");
                pass.setText(account.password);
                useSshKey.setChecked(false);
            }
            initialPath.setText(account.initialPath);
        }
    }

    private void browseSshKey() {
        File fi = null;
        final String currentPath = sshKeyPath.getText().toString().trim();
        if (!currentPath.isEmpty())
            fi = new File(currentPath).getParentFile();
        if (fi == null)
            fi = Environment.getExternalStorageDirectory().getAbsoluteFile();
        FileChooserDialog.show(AuthenticationActivity.this, fi);
    }

    private void invalidateTestConnectionEnabled() {
        if (host.getText().toString().trim().length() > 0 &&
                port.getText().toString().trim().length() > 0 &&
                user.getText().toString().trim().length() > 0) {
            testConnection.setEnabled(true);
        } else {
            testConnection.setEnabled(false);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_authentication, menu);
        menu.findItem(R.id.done).setVisible(testConnection.isEnabled());
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            cancel();
            return true;
        } else if (item.getItemId() == R.id.done) {
            done();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void toggleViews(final boolean enabled, @StringRes final int testConnectionText) {
        toggleViews(enabled, testConnectionText, false);
    }

    private void toggleViews(final boolean enabled, @StringRes final int testConnectionText, final boolean closeProgress) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                testConnection.setEnabled(enabled);
                host.setEnabled(enabled);
                port.setEnabled(enabled);
                user.setEnabled(enabled);
                pass.setEnabled(enabled);
                useSshKey.setEnabled(enabled);
                sshKeyPath.setEnabled(enabled);
                sshKeyPass.setEnabled(enabled);
                initialPath.setEnabled(enabled);
                testConnection.setText(testConnectionText);
                if (closeProgress && mProgress != null) {
                    mProgress.dismiss();
                    mProgress = null;
                }
            }
        });
    }

    private void done() {
        if (!mTestedConnection) {
            testConnection(true);
            return;
        }

        final SftpAccount account = new SftpAccount();
        account.host = host.getText().toString();
        account.port = Integer.parseInt(port.getText().toString().trim());
        account.username = user.getText().toString();
        if (useSshKey.isChecked()) {
            account.sshKeyPath = sshKeyPath.getText().toString();
            account.sshKeyPassword = sshKeyPass.getText().toString();
            account.password = null;
        } else {
            account.sshKeyPath = null;
            account.sshKeyPassword = null;
            account.password = pass.getText().toString();
        }
        account.initialPath = initialPath.getText().toString();

        setInitialPath(account.initialPath);
        setAccountDisplay(account.username + "@" + account.host);

        if (isSettings()) {
            account.id = Integer.parseInt(getAccountId());
            if (!AccountProvider.update(this, account, false)) {
                showError(new Exception("Unexpected error, account not found: " + account.id));
                return;
            }
            finish(getAccountId());
        } else {
            if (!isAddingAccount())
                AccountProvider.clear(this);
            final String accountId = AccountProvider.add(this, account);
            finish(accountId);
        }
    }

    private void showError(final Throwable e) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new MaterialDialog.Builder(AuthenticationActivity.this)
                        .title(R.string.error)
                        .content(e.getLocalizedMessage())
                        .positiveText(android.R.string.ok)
                        .cancelable(false)
                        .show();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mThread != null && !mThread.isInterrupted())
            mThread.interrupt();
        if (mProgress != null) {
            mProgress.dismiss();
            mProgress = null;
        }
    }

    // Test Connection
    @Override
    public void onClick(View v) {
        InputMethodManager mgr = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        mgr.hideSoftInputFromWindow(testConnection.getWindowToken(), 0);
        testConnection(false);
    }

    private void testConnection(final boolean doneAfter) {
        toggleViews(false, R.string.testing_connection);

        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(AuthenticationActivity.this);
        if (useSshKey.isChecked())
            prefs.edit().putString("last_ssh_key", sshKeyPath.getText().toString().trim()).apply();
        else
            prefs.edit().remove("last_ssh_key").apply();

        mProgress = ProgressDialogFragment.create(R.string.testing_connection).show(this);
        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                java.util.Properties config = new java.util.Properties();
                config.put("StrictHostKeyChecking", "no");

                JSch ssh = new JSch();
                Session session = null;
                ChannelSftp channel = null;

                if (useSshKey.isChecked()) {
                    try {
                        if (!sshKeyPass.getText().toString().trim().isEmpty()) {
                            ssh.addIdentity(sshKeyPath.getText().toString(),
                                    sshKeyPass.getText().toString());
                        } else {
                            ssh.addIdentity(sshKeyPath.getText().toString());
                        }
                        config.put("PreferredAuthentications", "publickey");
                    } catch (Throwable e) {
                        e.printStackTrace();
                        showError(e);
                        toggleViews(true, R.string.error_retry, true);
                        return;
                    }
                } else {
                    config.put("PreferredAuthentications", "password");
                }

                try {
                    session = ssh.getSession(user.getText().toString(),
                            host.getText().toString(),
                            Integer.parseInt(port.getText().toString()));
                    session.setConfig(config);
                    if (!useSshKey.isChecked())
                        session.setPassword(pass.getText().toString());
                    session.connect();
                    channel = (ChannelSftp) session.openChannel("sftp");
                    channel.connect();

                    if (!initialPath.getText().toString().trim().isEmpty())
                        channel.cd(initialPath.getText().toString());
                    toggleViews(true, R.string.successful, true);
                    mTestedConnection = true;
                    if (doneAfter) done();
                } catch (Throwable e) {
                    e.printStackTrace();
                    showError(e);
                    toggleViews(true, R.string.error_retry, true);
                    mTestedConnection = false;
                    if (doneAfter) showError(e);
                } finally {
                    if (session != null)
                        session.disconnect();
                    if (channel != null)
                        channel.disconnect();
                }
            }
        });
        mThread.start();
    }

    @Override
    public void onChoice(File file) {
        sshKeyPath.setText("");
        sshKeyPath.append(file.getAbsolutePath());
    }
}