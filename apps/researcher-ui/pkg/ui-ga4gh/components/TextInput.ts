import React, { JSX, ReactNode } from "react";
export interface TextInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
    subtitle?: ReactNode;
    helper?: ReactNode;
}
declare const TextInput: ({ className, title, subtitle, disabled, type, ...props }: TextInputProps) => JSX.Element;
export default TextInput;
