import type { NextPage } from 'next'
import { LogoutLink } from "../../pkg"

const HomeLoggedIn: NextPage = () => {
    const onLogout = LogoutLink()

    return (
        <div>
            <h1>YOU ARE LOGGED IN!</h1>
            <button
                className="btn btn-accent"
                onClick={onLogout}>
                Log out
            </button>
        </div>
    )
}

export default HomeLoggedIn