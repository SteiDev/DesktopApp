-ScreenManager.java is used to handle switching from one screen to another

----------- login -----------
-handles the username, email, password
-*login* works if account exists, (if doesnt exist either wont do anything, or automatically create a new account)
-*register* makes a new account, (if exists, enter the account automatically)
-*enter as Guest* wont save things idk not my expertise oopsie
-goes to the [main_menu] screen

-------- main_menu ---------
-clicking on the *username* switches to [account] screen
-*synthesizer* creates a dropdown option, 3 choices that switch to the respective screens: [organ], [acoustic_prand_piano], [synth_lead]
-*quiz* creates a dropdown option, 2 choices that switch to the respective screens: [start_quiz], [review_past_quiz]
-*logout* goes back to the [login] screen

-MainMenuController.java is used to handle the dropdown functions and wiring the connections between the screens
 
--------- account ----------
-