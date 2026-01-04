package com.modarb.android.ui.onboarding.activities

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import com.modarb.android.R
import com.modarb.android.databinding.ActivityWelcomeScreenBinding
import com.modarb.android.network.RetrofitService
import com.modarb.android.network.RetrofitService.handleRequest
import com.modarb.android.ui.home.HomeActivity
import com.modarb.android.ui.onboarding.models.Preferences
import com.modarb.android.ui.onboarding.models.data
import com.modarb.android.ui.onboarding.models.user
import com.modarb.android.ui.onboarding.utils.UserPref.UserPrefUtil
import com.modarb.android.ui.onboarding.utils.ValidationUtil
import com.modarb.android.ui.onboarding.viewModel.UserRepository
import com.modarb.android.ui.onboarding.viewModel.UserViewModel
import com.modarb.android.ui.onboarding.viewModel.UserViewModelFactory


class WelcomeScreenActivity : AppCompatActivity() {
    private lateinit var binding: ActivityWelcomeScreenBinding
    private lateinit var bottomSheet: BottomSheetDialog
    lateinit var viewModel: UserViewModel
    private lateinit var progress: ProgressBar
    private lateinit var loginBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWelcomeScreenBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)
        init()
        initServerDialog()
        initViewModels()
        onRegister()
    }

    private fun initServerDialog() {
        binding.titleTv.setOnClickListener {
            val builder = AlertDialog.Builder(this)
            builder.setTitle("Change Server Link")

            val input = EditText(this)
            input.hint = "Enter new server link"
            input.setText(RetrofitService.BASE_URL)
            builder.setView(input)
            builder.setPositiveButton("OK") { dialog, which ->
                val newUrl = input.text.toString()
                if (newUrl.isNotEmpty()) {
                    RetrofitService.changeBaseUrl(newUrl)
                    var i = Intent(this, WelcomeScreenActivity::class.java)
                    startActivity(i)
                    finish()
                }
            }
            builder.setNegativeButton("Cancel") { dialog, which -> dialog.cancel() }

            builder.show()
        }
    }

    private fun init() {

        initBottomSheet()
        binding.loginTextView.setOnClickListener {
            if (!bottomSheet.isShowing) {
                showLogin()
            }
        }

        binding.startButton.setOnClickListener {
            val intent = Intent(this, OnBoardingSplashActivity::class.java)
            startActivity(intent)
        }
    }

    private fun initBottomSheet() {
        bottomSheet = BottomSheetDialog(this)
    }

    private fun initViewModels() {
        val userRepository = UserRepository(
            RetrofitService.createService(),
            context = this,
            useLocalStorage = true // POC mode - using local storage
        )
        viewModel = ViewModelProvider(
            this, UserViewModelFactory(userRepository)
        )[UserViewModel::class.java]
    }

    private fun onRegister() {
        val showLogin = intent.getBooleanExtra("register", false)
        if (showLogin) showLogin()
    }

    private fun showLogin() {
        bottomSheet.setContentView(R.layout.login_view)
        bottomSheet.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.show()

        loginBtn = bottomSheet.findViewById<Button>(R.id.loginBtn)!!
        val emailEditText = bottomSheet.findViewById<TextInputEditText>(R.id.emailEditText)
        val passwordEditText = bottomSheet.findViewById<TextInputEditText>(R.id.passwordEditText)
        progress = bottomSheet.findViewById(R.id.progress)!!
        emailEditText?.setText(DEMO_EMAIL)
        passwordEditText?.setText(DEMO_PASSWORD)

        loginBtn.setOnClickListener {
            val email = emailEditText?.text.toString().trim()
            val password = passwordEditText?.text.toString()
            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this, getString(R.string.please_fill_all_the_data), Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }
            if (!ValidationUtil.validateEmail(email)) {
                Toast.makeText(this, getString(R.string.email), Toast.LENGTH_SHORT).show()
            } else if (!ValidationUtil.validatePasswordLength(password)) {
                Toast.makeText(
                    this, getString(R.string.enter_at_least_8_characters), Toast.LENGTH_SHORT
                ).show()
            } else {
                if (isDemoLogin(email, password)) {
                    performDemoLogin()
                    return@setOnClickListener
                }
                // Do login
                showProgress(true)
                viewModel.loginUser(email, password)
            }
        }

        viewModel.loginResponse.observe(this) { response ->
            handleRequest(response = response, onSuccess = { loginResponse ->
                if (loginResponse.status == 200) {
                    UserPrefUtil.saveUserData(this, loginResponse.data)
                    UserPrefUtil.setUserLoggedIn(this, true)
                    startActivity(
                        Intent(
                            this@WelcomeScreenActivity, HomeActivity::class.java
                        )
                    )
                    Toast.makeText(this, "Welcome", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }, onError = { errorResponse ->
                val defaultErrorMessage = getString(R.string.an_error_occurred)
                val message = errorResponse?.errors?.firstOrNull() ?: errorResponse?.error
                ?: defaultErrorMessage
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            })
            showProgress(false)
        }

    }

    private fun showProgress(show: Boolean) {
        if (show) {
            progress.visibility = View.VISIBLE
            loginBtn.text = ""
        } else {
            progress.visibility = View.GONE
            loginBtn.text = getString(R.string.login)
        }

    }

    private fun isDemoLogin(email: String, password: String): Boolean {
        return email.equals(DEMO_EMAIL, ignoreCase = true) && password == DEMO_PASSWORD
    }

    private fun performDemoLogin() {
        val samplePreferences = Preferences(
            fitness_goal = "Recomposition",
            preferred_days = listOf("Mon", "Wed", "Fri", "Sat"),
            preferred_equipment = listOf("Bodyweight", "Dumbbells", "Resistance bands"),
            target_weight = 75,
            workout_frequency = 4,
            workout_place = "Home & Gym"
        )
        val sampleUser = user(
            age = 30,
            email = DEMO_EMAIL,
            fitness_level = "intermediate",
            gender = "male",
            height = 178,
            id = "demo-user",
            injuries = listOf("tight hips"),
            name = "Demo Athlete",
            preferences = samplePreferences,
            role = "user",
            weight = 72
        )
        val sampleData = data(token = "demo-token", user = sampleUser)
        UserPrefUtil.saveUserData(this, sampleData)
        UserPrefUtil.setUserLoggedIn(this, true)
        Toast.makeText(this, getString(R.string.demo_login_success), Toast.LENGTH_SHORT).show()
        bottomSheet.dismiss()
        startActivity(Intent(this@WelcomeScreenActivity, HomeActivity::class.java))
        finish()
    }

    companion object {
        private const val DEMO_EMAIL = "demo@modarb.ai"
        private const val DEMO_PASSWORD = "DemoPass123!"
    }
}

