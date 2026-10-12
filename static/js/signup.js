(async function () {
  'use strict';

  const auth = window.ZipaiAuth;
  const form = document.getElementById('signupForm');

  if (!auth || !form) return;
  await auth.ready;
  if (auth.getUser()) {
    window.location.replace(auth.resolvePage('index.html'));
    return;
  }

  const password = form.elements.password;
  const passwordConfirm = form.elements.passwordConfirm;

  function validatePasswordMatch() {
    const hasValues = password.value && passwordConfirm.value;
    const message = hasValues && password.value !== passwordConfirm.value ? '비밀번호가 일치하지 않습니다.' : '';
    passwordConfirm.setCustomValidity(message);
  }

  password.addEventListener('input', validatePasswordMatch);
  passwordConfirm.addEventListener('input', validatePasswordMatch);

  form.addEventListener('submit', async function (event) {
    event.preventDefault();
    validatePasswordMatch();

    if (!form.checkValidity()) {
      form.reportValidity();
      return;
    }

    const submit = form.querySelector('[type="submit"]');
    submit.disabled = true;
    try {
      await auth.signup({
        userId: form.elements.userId.value,
        email: form.elements.email.value,
        phone: form.elements.phone.value,
        password: form.elements.password.value
      });
      form.reset();
      window.location.href = auth.resolvePage('index.html');
    } catch (error) {
      let message = form.querySelector('.login-page-error');
      if (!message) {
        message = document.createElement('p');
        message.className = 'login-page-error';
        form.appendChild(message);
      }
      message.textContent = error.message;
    } finally {
      submit.disabled = false;
    }
  });
})();
