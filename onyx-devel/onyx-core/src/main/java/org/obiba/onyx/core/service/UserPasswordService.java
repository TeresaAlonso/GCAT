/*******************************************************************************
 * Copyright 2008(c) The OBiBa Consortium. All rights reserved.
 * 
 * This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package org.obiba.onyx.core.service;

import java.util.List;

import org.obiba.onyx.core.domain.user.User;
import org.obiba.onyx.core.service.impl.AuthenticationFailedException;
import org.springframework.context.MessageSourceResolvable;

/**
 * This service provides password generation, user authentication and the ability to reset passwords.
 * 
 * Implementations of this class are responsible for password reuse, validity period and other business rules. As such,
 * the implementing class must coordinate the other 2 interfaces for completing the picture of password management. For
 * example, after a new password is generated by the IPasswordValidationStrategy, it must be hashed and then checked for
 * reuse before it is actually returned to the caller.
 */
public interface UserPasswordService {

  /**
   * Generates a password for the supplied user.
   * @param user The user the password is being generated for.
   * @return A plain text password.
   */
  String generatePassword(User user);

  /**
   * Validates that a plain text password respects the password construction rules.
   * @param user Password is being validated for this user.
   * @param password The plain text password.
   * @return A List of error messages. An empty List for success.
   */
  List<MessageSourceResolvable> validatePassword(User user, String password);

  /**
   * Assigns the given plain-text password to the given User. This method should validate the password, check for reuse,
   * hash it and add it to the user's password list.
   * @param user User to be associated with the password.
   * @param password New plain text password to be associated with the user.
   * @return A List of errors, or an empty List if the operation was successful.
   */
  List<MessageSourceResolvable> assignPassword(User user, String password);

  /**
   * Checks a plain text password against for a given login name. Must hash the password before checking.
   * @param login The user's login name.
   * @param password The supplied password in plain text.
   * @return User associated with the password.
   * @throws AuthenticationFailedException Thrown when authentication fails.
   */
  User authenticate(String login, String password) throws AuthenticationFailedException;

  /**
   * Resets a password to a freshly generated password.
   * @param user User associated with the password.
   * @return The new password in plain text.
   */
  String resetPassword(User user);

  /**
   * Shortcut method that returns true when a passwords needs to be changed. This is at least based on the password's
   * validity period.
   * @param user User associated with the password.
   * @return True if a password change is required, false otherwise.
   */
  boolean isNewPasswordRequired(User user);

  /**
   * This method returns the number of days left before their current password is to be invalidated and must be changed.
   * The value 0 or below means that the password has expired and should be changed. Certain password strategies may
   * require users to update their passwords at regular intervals.
   * @param user User associated with the password.
   * @return Number of days left before the password will be invalidated.
   */
  int getPasswordValidDaysLeft(User user);
}
