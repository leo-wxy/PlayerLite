package com.wxy.playerlite.feature.user

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wxy.playerlite.R
import com.wxy.playerlite.ui.theme.PlayerLiteTheme

internal const val LOGIN_WELCOME_TITLE = "登录后解锁在线播放"
internal const val LOGIN_WELCOME_SUBTITLE = "本地播放仍可直接使用"

class LoginActivity : ComponentActivity() {
    private val viewModel: LoginViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val state = viewModel.uiStateFlow.collectAsStateWithLifecycle().value
            LaunchedEffect(state.loginSucceeded) {
                if (state.loginSucceeded) {
                    viewModel.consumeLoginSuccess()
                    finish()
                }
            }
            LaunchedEffect(state.skipRequested) {
                if (state.skipRequested) {
                    viewModel.consumeSkipRequested()
                    finish()
                }
            }
            PlayerLiteTheme {
                LoginScreen(
                    state = state,
                    onLoginMethodSelected = viewModel::selectLoginMethod,
                    onPhoneChanged = viewModel::updatePhone,
                    onEmailChanged = viewModel::updateEmail,
                    onPasswordChanged = viewModel::updatePassword,
                    onSubmitLogin = viewModel::submitLogin,
                    onSkip = viewModel::skipLogin,
                    onLogout = viewModel::logout
                )
            }
        }
    }

    companion object {
        fun createIntent(context: Context): Intent {
            return Intent(context, LoginActivity::class.java)
        }
    }
}

@Composable
internal fun LoginScreen(
    state: LoginUiState,
    onLoginMethodSelected: (LoginMethod) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmitLogin: () -> Unit,
    onSkip: () -> Unit,
    onLogout: () -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = Color.Transparent
    ) { innerPadding ->
        AccountPageBackground(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            OutlinedButton(
                onClick = onSkip,
                enabled = !state.isBusy,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .defaultMinSize(minWidth = 82.dp, minHeight = 44.dp)
                    .testTag("login_skip_button"),
                border = BorderStroke(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.48f)
                ),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "跳过",
                    style = MaterialTheme.typography.titleSmall,
                    color = AccountVisualStyle.accentDeepColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .testTag("login_scroll_content")
                    .padding(
                        start = AccountVisualStyle.contentHorizontalPadding,
                        top = 52.dp,
                        end = AccountVisualStyle.contentHorizontalPadding,
                        bottom = 24.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LoginHeroPanel(state = state)
                AccountCardSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = AccountVisualStyle.contentMaxWidth)
                        .testTag("login_form_card")
                ) {
                    LoginFormCard(
                        state = state,
                        onLoginMethodSelected = onLoginMethodSelected,
                        onPhoneChanged = onPhoneChanged,
                        onEmailChanged = onEmailChanged,
                        onPasswordChanged = onPasswordChanged,
                        onSubmitLogin = onSubmitLogin,
                        onLogout = onLogout
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun LoginHeroPanel(state: LoginUiState) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = AccountVisualStyle.contentMaxWidth)
            .testTag("login_hero_panel")
            .shadow(18.dp, RoundedCornerShape(AccountVisualStyle.heroCorner), clip = false)
            .clip(RoundedCornerShape(AccountVisualStyle.heroCorner))
            .background(accountHeroBrush())
            .padding(horizontal = 22.dp, vertical = 20.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccountStatusChip(
                text = if (state.isLoggedIn) "在线音乐身份已连接" else "PlayerLite 在线音乐身份",
                modifier = Modifier.testTag("login_status_chip")
            )
            LoginHeroArtwork()
            Text(
                text = LOGIN_WELCOME_TITLE,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("login_welcome_title")
            )
            Text(
                text = LOGIN_WELCOME_SUBTITLE,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
                modifier = Modifier.testTag("login_welcome_subtitle")
            )
        }
    }
}

@Composable
private fun LoginHeroArtwork() {
    Box(
        modifier = Modifier.size(AccountVisualStyle.heroArtworkSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset(y = 10.dp)
                .shadow(26.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
        )
        Box(
            modifier = Modifier
                .size(152.dp)
                .clip(CircleShape)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.94f),
                            Color(0xFFFFE8E4)
                        )
                    )
                )
        )
        Image(
            painter = painterResource(R.drawable.ic_playerlite_brand),
            contentDescription = null,
            modifier = Modifier.size(110.dp)
        )
    }
}

@Composable
private fun LoginFormCard(
    state: LoginUiState,
    onLoginMethodSelected: (LoginMethod) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onSubmitLogin: () -> Unit,
    onLogout: () -> Unit
) {
    LoginMethodToggle(
        selected = state.loginMethod,
        onSelected = onLoginMethodSelected
    )

    if (state.loginMethod == LoginMethod.PHONE) {
        LoginInputField(
            value = state.phone,
            onValueChange = onPhoneChanged,
            label = "手机号",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.PhoneAndroid,
                    contentDescription = null
                )
            },
            enabled = !state.isBusy
        )
    } else {
        LoginInputField(
            value = state.email,
            onValueChange = onEmailChanged,
            label = "邮箱",
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.AlternateEmail,
                    contentDescription = null
                )
            },
            enabled = !state.isBusy,
            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Email)
        )
    }

    LoginInputField(
        value = state.password,
        onValueChange = onPasswordChanged,
        label = "密码",
        leadingIcon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null
            )
        },
        enabled = !state.isBusy,
        visualTransformation = PasswordVisualTransformation()
    )

    AccountPrimaryButton(
        text = if (state.isBusy) "登录中..." else "登录",
        onClick = onSubmitLogin,
        enabled = !state.isBusy,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("login_primary_button")
    )

    Text(
        text = state.statusText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )

    if (state.isLoggedIn) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TextButton(
                onClick = onLogout,
                enabled = !state.isBusy
            ) {
                Text(
                    text = "退出登录",
                    color = AccountVisualStyle.accentColor
                )
            }
        }
    }
}

@Composable
private fun LoginMethodToggle(
    selected: LoginMethod,
    onSelected: (LoginMethod) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = AccountVisualStyle.accentSoftColor
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LoginMethodOption(
                modifier = Modifier.weight(1f),
                label = "手机号",
                selected = selected == LoginMethod.PHONE,
                onClick = { onSelected(LoginMethod.PHONE) }
            )
            LoginMethodOption(
                modifier = Modifier.weight(1f),
                label = "邮箱",
                selected = selected == LoginMethod.EMAIL,
                onClick = { onSelected(LoginMethod.EMAIL) }
            )
        }
    }
}

@Composable
private fun LoginMethodOption(
    modifier: Modifier = Modifier,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier.testTag(
            if (label == "手机号") "login_method_phone_tab" else "login_method_email_tab"
        ),
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) AccountVisualStyle.accentColor else Color.Transparent,
        tonalElevation = if (selected) 2.dp else 0.dp,
        shadowElevation = if (selected) 4.dp else 0.dp
    ) {
        Box(
            modifier = Modifier.padding(vertical = 13.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) {
                    Color.White
                } else {
                    AccountVisualStyle.accentTextColor
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LoginInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    enabled: Boolean,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = leadingIcon,
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(20.dp),
        keyboardOptions = keyboardOptions,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AccountVisualStyle.accentColor,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
            focusedLabelColor = AccountVisualStyle.accentColor,
            focusedLeadingIconColor = AccountVisualStyle.accentColor,
            unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White
        )
    )
}
