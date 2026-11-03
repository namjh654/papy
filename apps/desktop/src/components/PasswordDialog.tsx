import { useEffect, useRef, useState } from "react";
import { Icon } from "./Icon";
import { t } from "../i18n";

interface Props {
  fileName: string;
  /** 직전에 입력한 암호가 틀렸는지 */
  retry: boolean;
  onSubmit(password: string): void;
  onCancel(): void;
}

/** 암호 입력. 암호는 엔진으로만 넘기고 저장하지 않는다. */
export function PasswordDialog({ fileName, retry, onSubmit, onCancel }: Props) {
  const [password, setPassword] = useState("");
  const input = useRef<HTMLInputElement>(null);
  useEffect(() => input.current?.focus(), [retry]);

  return (
    <div className="backdrop" onKeyDown={(event) => event.key === "Escape" && onCancel()}>
      <form
        className="dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="password-title"
        onSubmit={(event) => {
          event.preventDefault();
          if (password) onSubmit(password);
        }}
      >
        <div className="dialog-icon">
          <Icon name="lock" size={22} />
        </div>
        <h2 id="password-title">{t.password.title}</h2>
        <p className="muted">
          <strong>{fileName}</strong>{t.password.prompt}
        </p>
        <input
          ref={input}
          type="password"
          autoComplete="off"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          aria-invalid={retry}
          aria-describedby={retry ? "password-error" : undefined}
        />
        {retry && (
          <p id="password-error" className="error-text">
            {t.password.wrong}
          </p>
        )}
        <div className="dialog-actions">
          <button type="button" className="ghost" onClick={onCancel}>
            {t.common.cancel}
          </button>
          <button type="submit" className="primary" disabled={!password}>
            {t.common.open}
          </button>
        </div>
      </form>
    </div>
  );
}
