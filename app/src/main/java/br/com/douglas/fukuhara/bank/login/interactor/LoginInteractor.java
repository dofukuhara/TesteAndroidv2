package br.com.douglas.fukuhara.bank.login.interactor;

import br.com.douglas.fukuhara.bank.login.Contract;
import br.com.douglas.fukuhara.bank.network.RestClient;
import br.com.douglas.fukuhara.bank.network.vo.LoginVo;
import br.com.douglas.fukuhara.bank.network.vo.UserAccount;
import br.com.douglas.fukuhara.bank.network.vo.UserError;
import br.com.douglas.fukuhara.bank.persistance.Storage;
import br.com.douglas.fukuhara.bank.utils.FormatterUtils;
import br.com.douglas.fukuhara.bank.utils.UsernameValidation;
import io.reactivex.disposables.CompositeDisposable;

import static br.com.douglas.fukuhara.bank.network.NetworkUtils.getObservableNetworkThread;
import static br.com.douglas.fukuhara.bank.utils.LoginUtils.isValidPasswordPattern;
import static br.com.douglas.fukuhara.bank.utils.LoginUtils.isValidUsernamePattern;

public class LoginInteractor implements Contract.LoginInteractorInput {

    private final CompositeDisposable mCompositeDisposable = new CompositeDisposable();
    private Contract.LoginPresenterInput mPresenter;
    private final RestClient mRestClient;
    private final Storage mStorage;

    public LoginInteractor(RestClient restClient, Storage storage) {
        mRestClient = restClient;
        mStorage = storage;
    }

    public void setPresenter(Contract.LoginPresenterInput presenter) {
        mPresenter = presenter;
    }

    @Override
    public void onLogin(String username, String password) {
        if (username.isEmpty()) {
            mPresenter.emptyUsername();
            return;
        }

        if (password.isEmpty()) {
            mPresenter.emptyPassword();
            return;
        }

        @UsernameValidation.Type int validUsername = isValidUsernamePattern(username);
        switch (validUsername) {
            case UsernameValidation.INVALID_CPF:
                mPresenter.invalidCpf();
                return;
            case UsernameValidation.INVALID_EMAIL_CPF:
                mPresenter.invalidEmailCpf();
                return;
            case UsernameValidation.VALID_CPF:
            case UsernameValidation.VALID_EMAIL:
                // If the informed username matches either in CPF or in Email pattern, lets
                // proceed in validating the password pattern
                break;
        }

        if (!isValidPasswordPattern(password)) {
            mPresenter.invalidPasswordType();
            return;
        }

        mCompositeDisposable.add(
                mRestClient.getApi().doLogin(username, password)
                        .compose(getObservableNetworkThread())
                        .subscribe(
                                (LoginVo loginVoResponse) -> onLoginResponse(loginVoResponse, username),
                                this::onLoginFailure));
    }

    private void onLoginResponse(LoginVo loginVoResponse, String username) {
        UserError userError = loginVoResponse.getUserError();
        if (userError != null) {
            String errorMsg = userError.getMessage();
            int errorCode = userError.getCode();

            if (errorMsg != null && !errorMsg.isEmpty() && errorCode != 0) {
                mPresenter.showLoginErrorMessage(FormatterUtils.formatLoginErrorMsg(errorMsg, errorCode));
                return;
            }
        }
        UserAccount userAccount = loginVoResponse.getUserAccount();
        if (userAccount != null) {
            if (userAccount.getUserId() == 0 &&
                    userAccount.getAgency() == null &&
                    userAccount.getBalance() == null &&
                    userAccount.getBankAccount() == null &&
                    userAccount.getName() == null) {
                mPresenter.showLoginGenericError();
                return;
            }
        }
        mStorage.saveLogin(username);
        mPresenter.onSuccessfulLoginResponse(userAccount);
    }

    private void onLoginFailure(Throwable throwable) {
        String errorMessage = throwable.getMessage();
        if (errorMessage != null && !errorMessage.isEmpty()) {
            mPresenter.showLoginErrorMessage(errorMessage);
        } else {
            mPresenter.showLoginGenericError();
        }
    }

    @Override
    public void checkForPreviousLoggedUser() {
        String lastSavedUser = mStorage.getLogin();
        if (lastSavedUser != null && !lastSavedUser.isEmpty()) {
            mPresenter.setLoginFromPreviousLoggedUser(lastSavedUser);
        }
    }

    @Override
    public void disposeAll() {
        mCompositeDisposable.clear();
    }
}
