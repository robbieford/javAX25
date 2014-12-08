#!/usr/bin/env python
# -*- coding: utf-8 -*-

# ***************************************************************************
# *   Copyright (C) 2011, Paul Lutus                                        *
# *                                                                         *
# *   This program is free software; you can redistribute it and/or modify  *
# *   it under the terms of the GNU General Public License as published by  *
# *   the Free Software Foundation; either version 2 of the License, or     *
# *   (at your option) any later version.                                   *
# *                                                                         *
# *   This program is distributed in the hope that it will be useful,       *
# *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
# *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the         *
# *   GNU General Public License for more details.                          *
# *                                                                         *
# *   You should have received a copy of the GNU General Public License     *
# *   along with this program; if not, write to the                         *
# *   Free Software Foundation, Inc.,                                       *
# *   59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.             *
# ***************************************************************************

import math

class Biquad:

  def __init__(self, freq, srate, Q):
      freq = float(freq)
      self.srate = float(srate)
      Q = float(Q)
      self.a0 = self.a1 = self.a2 = 0
      self.b0 = self.b1 = self.b2 = 0
      self.x1 = self.x2 = 0
      self.y1 = self.y2 = 0
      # only used for peaking and shelving filter types
      omega = 2 * math.pi * freq / self.srate
      sn = math.sin(omega)
      cs = math.cos(omega)
      alpha = sn / (2*Q)
      self.b0 = (1 - cs) /2
      self.b1 = 1 - cs
      self.b2 = (1 - cs) /2
      self.a0 = 1 + alpha
      self.a1 = -2 * cs
      self.a2 = 1 - alpha
      # prescale constants
      self.b0 /= self.a0
      self.b1 /= self.a0
      self.b2 /= self.a0
      self.a1 /= self.a0
      self.a2 /= self.a0

#  def lowpass(self,A,omega,sn,cs,alpha,beta):
    
  # perform filtering function
  def __call__(self,x):
    y = self.b0 * x + self.b1 * self.x1 + self.b2 * self.x2 - self.a1 * self.y1 - self.a2 * self.y2
    #self.x2, self.x1 = self.x1, x
    self.x2 = self.x1
    self.x1 = x
    #self.y2, self.y1 = self.y1, y
    self.y2 = self.y1
    self.y1 = y
    return y
