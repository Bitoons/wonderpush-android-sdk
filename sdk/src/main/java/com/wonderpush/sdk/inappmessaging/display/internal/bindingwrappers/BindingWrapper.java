// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.wonderpush.sdk.inappmessaging.display.internal.bindingwrappers;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.Button;
import android.widget.ImageView;

import com.wonderpush.sdk.inappmessaging.display.internal.InAppMessageLayoutConfig;
import com.wonderpush.sdk.inappmessaging.display.internal.Logging;
import com.wonderpush.sdk.inappmessaging.model.InAppMessage;

import java.util.List;

/**
 * View container for all in app message layouts This container serves as an abstraction around
 * bindings created by individual views.
 *
 * @hide
 */
public abstract class BindingWrapper {
  protected final InAppMessage message;
  final InAppMessageLayoutConfig config;
  final LayoutInflater inflater;

  protected BindingWrapper(
          InAppMessageLayoutConfig config, LayoutInflater inflater, InAppMessage message) {
    this.config = config;
    this.inflater = inflater;
    this.message = message;
  }

  @NonNull
  public abstract ImageView getImageView();

  @NonNull
  public abstract ViewGroup getRootView();

  @NonNull
  public abstract View getDialogView();

  @Nullable
  public abstract OnGlobalLayoutListener inflate(
          List<OnClickListener> actionListeners, OnClickListener dismissOnClickListener);

  public boolean canSwipeToDismiss() {
    return false;
  }

  @Nullable
  public OnClickListener getDismissListener() {
    return null;
  }

  @NonNull
  public InAppMessageLayoutConfig getConfig() {
    return config;
  }

  protected void setViewBgColorFromHex(@Nullable View view, @Nullable String hexColor) {
    if (view == null || TextUtils.isEmpty(hexColor)) return;
    try {
      view.setBackgroundColor(Color.parseColor(hexColor));
    } catch (IllegalArgumentException e) {
      // If the color didnt parse correctly, fail 'open', with default background color
      Logging.loge("Error parsing background color: " + e.toString() + " color: " + hexColor);
    }
  }

  public static void setButtonBgColorFromHex(Button button, String hexColor) {
    try {
      if (hexColor == null) return;
      Drawable drawable = button.getBackground();
      Drawable compatDrawable = DrawableCompat.wrap(drawable);
      if (!TextUtils.isEmpty(hexColor)) DrawableCompat.setTint(compatDrawable, Color.parseColor(hexColor));
      button.setBackground(compatDrawable);
    } catch (IllegalArgumentException e) {
      // If the color didnt parse correctly, fail 'open', with default background color
      Logging.loge("Error parsing background color: " + e.toString());
    }
  }

  // Worth changing the API allow for building a UI model from a data model. Future change.
  public static void setupViewButtonFromModel(
      Button viewButton, com.wonderpush.sdk.inappmessaging.model.Button modelButton) {
    String buttonTextHexColor = modelButton.getText().getHexColor();
    if (modelButton.getButtonHexColor() != null) setButtonBgColorFromHex(viewButton, modelButton.getButtonHexColor());
    viewButton.setText(modelButton.getText().getText());
    if (!TextUtils.isEmpty(buttonTextHexColor)) viewButton.setTextColor(Color.parseColor(buttonTextHexColor));
  }

  protected void setButtonActionListener(
      @Nullable Button button, OnClickListener actionListener) {
    if (button != null) {
      button.setOnClickListener(actionListener);
    }
  }
}
